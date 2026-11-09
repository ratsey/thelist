package app.trolley.lists.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.trolley.lists.BuildConfig
import app.trolley.lists.R
import app.trolley.lists.data.AppContainer
import app.trolley.lists.data.Item
import app.trolley.lists.data.ItemRepository
import app.trolley.lists.data.SettingsStore
import app.trolley.lists.update.DownloadState
import app.trolley.lists.update.InstallResult
import app.trolley.lists.update.ReleaseInfo
import app.trolley.lists.update.UpdateCheck
import app.trolley.lists.update.UpdateManager
import app.trolley.lists.update.isNewerThan
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data object Checking : UpdateUi
    data class Available(val version: String) : UpdateUi
    data class Downloading(val version: String, val percent: Int) : UpdateUi
    data class ReadyToInstall(val version: String) : UpdateUi
    data class NeedsPermission(val version: String) : UpdateUi
    data class Failed(@StringRes val messageRes: Int) : UpdateUi
}

data class UiMessage(@StringRes val textRes: Int, val undoable: Boolean = false)

class ListViewModel(
    private val repository: ItemRepository,
    private val updates: UpdateManager,
    private val settings: SettingsStore,
) : ViewModel() {

    val activeItems: StateFlow<List<Item>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyItems: StateFlow<List<Item>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _hintVisible = MutableStateFlow(true)
    val hintVisible: StateFlow<Boolean> = _hintVisible.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private var pendingRelease: ReleaseInfo? = null
    private var downloadJob: Job? = null
    private var lastUndo: Undo? = null

    private sealed interface Undo {
        data class Moved(val item: Item, val nowActive: Boolean) : Undo
        data class Deleted(val item: Item) : Undo
        data class ClearedHistory(val items: List<Item>) : Undo
    }

    init {
        resumePendingDownload()
        checkForUpdates(userInitiated = false)
    }

    // ---------------------------------------------------------------- list edits

    fun addItem(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch { repository.add(name) }
    }

    /** Moves an item to the other list: active -> History, or History -> active. */
    fun move(item: Item) {
        val toHistory = item.completedAt == null
        viewModelScope.launch {
            if (toHistory) repository.complete(item) else repository.restore(item)
            lastUndo = Undo.Moved(item, nowActive = !toHistory)
            _hintVisible.value = false
            _messages.send(
                UiMessage(
                    textRes = if (toHistory) R.string.msg_moved_history else R.string.msg_moved_active,
                    undoable = true,
                ),
            )
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            repository.delete(item)
            lastUndo = Undo.Deleted(item)
            _messages.send(UiMessage(R.string.msg_deleted, undoable = true))
        }
    }

    fun rename(item: Item, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty() || name == item.name) return
        viewModelScope.launch {
            repository.rename(item, name)
            lastUndo = null
            _messages.send(UiMessage(R.string.msg_renamed))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val removed = repository.clearHistory()
            if (removed.isEmpty()) return@launch
            lastUndo = Undo.ClearedHistory(removed)
            _messages.send(UiMessage(R.string.msg_history_cleared, undoable = true))
        }
    }

    fun undoLast() {
        val undo = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            when (undo) {
                is Undo.Moved ->
                    if (undo.nowActive) repository.restore(undo.item) else repository.complete(undo.item)

                is Undo.Deleted -> repository.restoreAll(listOf(undo.item))
                is Undo.ClearedHistory -> repository.restoreAll(undo.items)
            }
        }
    }

    fun dismissHint() {
        _hintVisible.value = false
    }

    // ---------------------------------------------------------------- self update

    fun checkForUpdates(userInitiated: Boolean) {
        viewModelScope.launch {
            if (userInitiated) _update.value = UpdateUi.Checking

            when (val result = updates.check(force = userInitiated)) {
                is UpdateCheck.Available -> {
                    pendingRelease = result.release
                    _update.value = UpdateUi.Available(result.release.version)
                }

                UpdateCheck.UpToDate -> {
                    _update.value = UpdateUi.Hidden
                    if (userInitiated) _messages.send(UiMessage(R.string.update_latest))
                }

                UpdateCheck.NotConfigured -> {
                    _update.value = UpdateUi.Hidden
                    if (userInitiated) _messages.send(UiMessage(R.string.update_not_configured))
                }

                UpdateCheck.Unreachable -> {
                    _update.value = UpdateUi.Hidden
                    if (userInitiated) _messages.send(UiMessage(R.string.update_unavailable))
                }

                UpdateCheck.Skipped -> Unit
            }
        }
    }

    fun startUpdate() {
        val release = pendingRelease ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _update.value = UpdateUi.Downloading(release.version, 0)
            val downloadId = updates.startDownload(release)
            if (downloadId == null) {
                _update.value = UpdateUi.Failed(R.string.update_unavailable)
                return@launch
            }
            pollDownload(downloadId, release.version)
        }
    }

    fun installUpdate() {
        val version = when (val state = _update.value) {
            is UpdateUi.ReadyToInstall -> state.version
            is UpdateUi.NeedsPermission -> state.version
            else -> return
        }
        when (updates.install(version)) {
            InstallResult.STARTED -> Unit
            InstallResult.NEEDS_PERMISSION -> _update.value = UpdateUi.NeedsPermission(version)
            InstallResult.FILE_MISSING, InstallResult.NO_INSTALLER -> {
                _update.value = UpdateUi.Failed(R.string.update_file_missing)
                viewModelScope.launch { updates.clearPendingDownload() }
            }
        }
    }

    fun openInstallPermissionSettings() = updates.openInstallPermissionSettings()

    fun dismissUpdate() {
        if (_update.value is UpdateUi.Downloading) return
        _update.value = UpdateUi.Hidden
    }

    private suspend fun pollDownload(downloadId: Long, version: String) {
        var lastPercent = -1
        while (true) {
            val status = updates.status(downloadId)
            when (status.state) {
                DownloadState.SUCCESS -> {
                    _update.value = UpdateUi.ReadyToInstall(version)
                    return
                }

                DownloadState.FAILED, DownloadState.MISSING -> {
                    _update.value = UpdateUi.Failed(R.string.update_unavailable)
                    updates.clearPendingDownload()
                    return
                }

                DownloadState.RUNNING -> {
                    if (status.percent != lastPercent) {
                        lastPercent = status.percent
                        _update.value = UpdateUi.Downloading(version, status.percent)
                    }
                    delay(400)
                }
            }
        }
    }

    /** Picks up a download that finished while the app was closed. */
    private fun resumePendingDownload() {
        viewModelScope.launch {
            val pending = settings.pendingDownload() ?: return@launch
            val (downloadId, version) = pending

            if (!version.isNewerThan(BuildConfig.VERSION_NAME)) {
                updates.clearPendingDownload()
                return@launch
            }

            when (updates.status(downloadId).state) {
                DownloadState.SUCCESS -> _update.value = UpdateUi.ReadyToInstall(version)
                DownloadState.RUNNING -> {
                    _update.value = UpdateUi.Downloading(version, 0)
                    pollDownload(downloadId, version)
                }

                else -> updates.clearPendingDownload()
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ListViewModel(container.repository, container.updates, container.settings) as T
            }
    }
}
