package com.ratsey.thelist.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ratsey.thelist.BuildConfig
import com.ratsey.thelist.R
import com.ratsey.thelist.data.AppContainer
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.ItemRepository
import com.ratsey.thelist.data.PriceRow
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.data.PurchaseLine
import com.ratsey.thelist.data.Receipt
import com.ratsey.thelist.data.ReceiptRepository
import com.ratsey.thelist.data.SettingsStore
import com.ratsey.thelist.data.Store
import com.ratsey.thelist.data.StoreRepository
import com.ratsey.thelist.receipt.ItemMatcher
import com.ratsey.thelist.receipt.ReceiptOcr
import com.ratsey.thelist.receipt.ReceiptParser
import com.ratsey.thelist.sync.SyncClient
import com.ratsey.thelist.sync.SyncEngine
import com.ratsey.thelist.data.SyncSettingsRepository
import com.ratsey.thelist.update.DownloadState
import com.ratsey.thelist.update.InstallResult
import com.ratsey.thelist.update.ReleaseInfo
import com.ratsey.thelist.update.UpdateCheck
import com.ratsey.thelist.update.UpdateManager
import com.ratsey.thelist.update.isNewerThan
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

sealed interface ScanUi {
    data object Idle : ScanUi
    data object Reading : ScanUi
    data class Failed(@StringRes val messageRes: Int) : ScanUi
    data class Review(val draft: ReceiptDraft) : ScanUi
}

data class ReceiptDraft(
    val photoPath: String?,
    val storeName: String,
    val purchasedAt: Long,
    val currency: String,
    val total: Double?,
    val lines: List<DraftLine>,
    val markBought: Boolean = true,
)

data class DraftLine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val matchedItemId: String?,
    val matchedName: String?,
    /** True when the line matched something already swiped instead of something to buy. */
    val matchedFromBasket: Boolean = false,
    val include: Boolean = true,
)

enum class SyncState { Disabled, Idle, Syncing, Offline }

data class SyncUi(
    val state: SyncState = SyncState.Disabled,
    val code: String? = null,
    val serverUrl: String = "",
    val lastSyncedAt: Long = 0L,
)

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
    private val storeRepository: StoreRepository,
    private val receipts: ReceiptRepository,
    private val updates: UpdateManager,
    private val settings: SettingsStore,
    private val syncEngine: SyncEngine,
    private val syncSettings: SyncSettingsRepository,
    private val ocr: ReceiptOcr,
) : ViewModel() {

    val activeItems: StateFlow<List<Item>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val basketItems: StateFlow<List<Item>> = repository.observeBasket()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything off the list but kept: one tap from going back on it. */
    val shelfItems: StateFlow<List<Item>> = repository.observeShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shops, alphabetical, for saying where an item lives. */
    val stores: StateFlow<List<Store>> = storeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether a scan moves the items it matched out of the list. Set by the user in Settings. */
    val scanMovesItems: StateFlow<Boolean> = settings.scanMovesItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val priceRows: StateFlow<List<PriceRow>> = receipts.observePriceRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val priceSummaries: StateFlow<List<PriceSummary>> = priceRows
        .map { ReceiptRepository.summarise(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedProduct = MutableStateFlow<String?>(null)

    val productDetail: StateFlow<ProductDetail?> = combine(priceRows, selectedProduct) { rows, key ->
        if (key == null) return@combine null
        val summary = ReceiptRepository.summarise(rows).firstOrNull { it.productKey == key }
            ?: return@combine null
        ProductDetail(
            summary = summary,
            points = ReceiptRepository.pointsFor(rows, key),
            purchases = rows.filter { it.productKey == key }.sortedByDescending { it.purchasedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _scan = MutableStateFlow<ScanUi>(ScanUi.Idle)
    val scan: StateFlow<ScanUi> = _scan.asStateFlow()

    private val _syncUi = MutableStateFlow(SyncUi())
    val syncUi: StateFlow<SyncUi> = _syncUi.asStateFlow()

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _hintVisible = MutableStateFlow(true)
    val hintVisible: StateFlow<Boolean> = _hintVisible.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private var pendingRelease: ReleaseInfo? = null
    private var downloadJob: Job? = null
    private var scanJob: Job? = null
    private var lastUndo: Undo? = null
    private var syncing = false

    private sealed interface Undo {
        data class Moved(val item: Item, val nowActive: Boolean) : Undo
        data class Deleted(val item: Item) : Undo
        data class PutAway(val item: Item) : Undo
        data class ClearedBasket(val items: List<Item>) : Undo
    }

    init {
        observeSyncConfig()
        startSyncLoop()
        resumePendingDownload()
        checkForUpdates(userInitiated = false)
    }

    // ---------------------------------------------------------------- list edits

    fun addItem(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            when (repository.add(name)) {
                ItemRepository.AddOutcome.AlreadyListed ->
                    _messages.send(UiMessage(R.string.msg_already_listed))

                ItemRepository.AddOutcome.Readded ->
                    _messages.send(UiMessage(R.string.msg_readded))

                ItemRepository.AddOutcome.Added -> Unit
            }
            scheduleSync()
        }
    }

    fun move(item: Item) {
        val toBasket = item.completedAt == null
        viewModelScope.launch {
            if (toBasket) repository.complete(item) else repository.restore(item)
            lastUndo = Undo.Moved(item, nowActive = !toBasket)
            _hintVisible.value = false
            _messages.send(
                UiMessage(
                    textRes = if (toBasket) R.string.msg_moved_basket else R.string.msg_moved_active,
                    undoable = true,
                ),
            )
            scheduleSync()
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            repository.delete(item)
            lastUndo = Undo.Deleted(item)
            _messages.send(UiMessage(R.string.msg_deleted, undoable = true))
            scheduleSync()
        }
    }

    fun rename(item: Item, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty() || name == item.name) return
        viewModelScope.launch {
            repository.rename(item, name)
            lastUndo = null
            _messages.send(UiMessage(R.string.msg_renamed))
            scheduleSync()
        }
    }

    // ------------------------------------------------------- shops and aisles

    fun addStore(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            storeRepository.add(name)
            _messages.send(UiMessage(R.string.msg_store_added))
            scheduleSync()
        }
    }

    fun renameStore(store: Store, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty() || name == store.name) return
        viewModelScope.launch {
            storeRepository.rename(store, name)
            _messages.send(UiMessage(R.string.msg_store_renamed))
            scheduleSync()
        }
    }

    fun deleteStore(store: Store) {
        viewModelScope.launch {
            storeRepository.delete(store)
            _messages.send(UiMessage(R.string.msg_store_deleted))
            scheduleSync()
        }
    }

    /**
     * Saves where an item lives: which shop, and where inside it. A shop typed straight into the
     * sheet is created first, so "Walmart, aisle A6" is one gesture instead of a detour through a
     * separate screen to set the shop up before it can be chosen.
     */
    fun setPlacement(item: Item, storeId: String?, newStoreName: String?, location: String) {
        viewModelScope.launch {
            val created = newStoreName?.let { storeRepository.add(it) }
            val target = created?.id ?: storeId
            repository.setPlacement(item, target, location)
            val cleared = target == null && location.isBlank()
            _messages.send(
                UiMessage(
                    if (cleared) R.string.msg_placement_cleared else R.string.msg_placement_saved,
                ),
            )
            scheduleSync()
        }
    }

    fun clearBasket() {
        viewModelScope.launch {
            val removed = repository.clearBasket()
            if (removed.isEmpty()) return@launch
            lastUndo = Undo.ClearedBasket(removed)
            _messages.send(UiMessage(R.string.msg_basket_cleared, undoable = true))
            scheduleSync()
        }
    }

    fun addFromShelf(item: Item) {
        viewModelScope.launch {
            repository.bringBack(item)
            _hintVisible.value = false
            _messages.send(UiMessage(R.string.msg_readded))
            scheduleSync()
        }
    }

    /** The only delete the sheet offers: the shelf is where rows accumulate, so it prunes. */
    fun deleteFromShelf(item: Item) {
        viewModelScope.launch {
            repository.delete(item)
            lastUndo = Undo.Deleted(item)
            _messages.send(UiMessage(R.string.msg_deleted, undoable = true))
            scheduleSync()
        }
    }

    /** Swiping left on either list: nothing is lost, the row just steps off the list. */
    fun putAway(item: Item) {
        viewModelScope.launch {
            repository.putAway(item)
            lastUndo = Undo.PutAway(item)
            _messages.send(UiMessage(R.string.msg_put_away, undoable = true))
            scheduleSync()
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
                is Undo.PutAway -> repository.restoreAll(listOf(undo.item))
                is Undo.ClearedBasket -> repository.restoreAll(undo.items)
            }
            scheduleSync()
        }
    }

    fun dismissHint() {
        _hintVisible.value = false
    }

    fun setScanMovesItems(value: Boolean) {
        viewModelScope.launch { settings.setScanMovesItems(value) }
    }

    // ---------------------------------------------------------------- receipts

    fun startScan(photoUri: Uri, photoPath: String) {
        scanJob?.cancel()
        _scan.value = ScanUi.Reading
        scanJob = viewModelScope.launch {
            val text = runCatching { ocr.readText(photoUri) }.getOrNull()
            if (text.isNullOrBlank()) {
                _scan.value = ScanUi.Failed(R.string.scan_failed)
                return@launch
            }

            val parsed = ReceiptParser.parse(text, dayFirst = isDayFirst())
            if (parsed.lines.isEmpty()) {
                _scan.value = ScanUi.Failed(R.string.scan_no_lines)
                return@launch
            }

            // Basket first: a swipe during the shop is the shopper's own record of buying it,
            // so it claims the line ahead of a name that merely looks familiar.
            val basket = repository.recentlyPickedUp()
            val candidates = repository.activeNow()
            val draft = ReceiptDraft(
                photoPath = photoPath,
                storeName = parsed.storeName.orEmpty(),
                purchasedAt = parsed.purchasedAt ?: System.currentTimeMillis(),
                currency = parsed.currency,
                total = parsed.total,
                markBought = settings.scanMovesItems.first(),
                lines = parsed.lines.map { line ->
                    val match = ItemMatcher.match(line.name, basket, candidates)
                    DraftLine(
                        name = line.name,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        lineTotal = line.lineTotal,
                        matchedItemId = match?.item?.id,
                        matchedName = match?.item?.name,
                        matchedFromBasket = match?.source == ItemMatcher.Source.Basket,
                    )
                },
            )
            _scan.value = ScanUi.Review(draft)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scan.value = ScanUi.Idle
    }

    fun dismissScanError() {
        if (_scan.value is ScanUi.Failed) _scan.value = ScanUi.Idle
    }

    fun updateDraft(transform: (ReceiptDraft) -> ReceiptDraft) {
        val review = _scan.value as? ScanUi.Review ?: return
        _scan.value = review.copy(draft = transform(review.draft))
    }

    fun saveDraft() {
        val review = _scan.value as? ScanUi.Review ?: return
        val draft = review.draft
        val included = draft.lines.filter { it.include && it.name.isNotBlank() }
        if (included.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val receipt = Receipt(
                storeName = draft.storeName.trim().ifBlank { "Unknown store" },
                purchasedAt = draft.purchasedAt,
                total = draft.total,
                currency = draft.currency,
                // Relative name only: this row is shared with other devices.
                photoPath = draft.photoPath?.let { File(it).name },
                createdAt = now,
                updatedAt = now,
            )

            val lines = included.map { line ->
                val displayName = line.matchedName ?: displayNameFor(line.name)
                PurchaseLine(
                    receiptId = receipt.id,
                    productKey = ItemMatcher.productKey(line.matchedName ?: line.name),
                    displayName = displayName,
                    sourceText = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    matchedItemId = line.matchedItemId,
                    createdAt = now,
                    updatedAt = now,
                )
            }

            // Only list matches have anywhere to move to: a basket match was swiped already.
            val matchedIds = included
                .filterNot { it.matchedFromBasket }
                .mapNotNull { it.matchedItemId }
                .toSet()
            val markBought = if (draft.markBought && matchedIds.isNotEmpty()) {
                repository.activeNow().filter { it.id in matchedIds }
            } else {
                emptyList()
            }

            receipts.saveReceipt(receipt, lines, markBought)
            _scan.value = ScanUi.Idle
            _messages.send(UiMessage(R.string.scan_saved))
            scheduleSync()
        }
    }

    fun openProduct(productKey: String) {
        selectedProduct.value = productKey
    }

    fun closeProduct() {
        selectedProduct.value = null
    }

    /**
     * Long-press on a row: what this thing has cost you. Prices are no longer a screen of their own,
     * so the row is the way in — and saying so is better than a long press that appears to do
     * nothing when nothing has been bought yet.
     */
    fun showPricesFor(item: Item) {
        val key = ItemMatcher.productKey(item.name)
        if (priceRows.value.any { it.productKey == key }) {
            selectedProduct.value = key
        } else {
            viewModelScope.launch { _messages.send(UiMessage(R.string.prices_none_for_item)) }
        }
    }

    // ---------------------------------------------------------------- sharing

    fun createSharedList(serverUrl: String, memberName: String) {
        val url = normalizeServerUrl(serverUrl)
        if (url.isBlank()) return
        viewModelScope.launch {
            _syncUi.update { it.copy(state = SyncState.Syncing) }
            runCatching {
                SyncClient(url).createList(memberName.trim().ifBlank { "Someone" }, "Groceries")
            }.onSuccess { credentials ->
                syncSettings.enable(url, credentials.code, credentials.token, credentials.name)
                syncEngine.enqueueSnapshot()
                performSync(silent = true)
                _messages.send(UiMessage(R.string.sync_created))
            }.onFailure {
                _syncUi.update { state -> state.copy(state = SyncState.Offline) }
                _messages.send(UiMessage(R.string.sync_failed))
            }
        }
    }

    fun joinSharedList(serverUrl: String, code: String, memberName: String) {
        val url = normalizeServerUrl(serverUrl)
        val shareCode = code.trim().uppercase(Locale.UK)
        if (url.isBlank() || shareCode.isBlank()) return
        viewModelScope.launch {
            _syncUi.update { it.copy(state = SyncState.Syncing) }
            runCatching {
                SyncClient(url).joinList(shareCode, memberName.trim().ifBlank { "Someone" })
            }.onSuccess { credentials ->
                syncSettings.enable(url, credentials.code, credentials.token, credentials.name)
                runCatching { syncEngine.replaceWithRemote() }
                performSync(silent = true)
                _messages.send(UiMessage(R.string.sync_joined))
            }.onFailure {
                _syncUi.update { state -> state.copy(state = SyncState.Offline) }
                _messages.send(UiMessage(R.string.sync_failed))
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch { performSync(silent = false) }
    }

    fun disconnectSync() {
        viewModelScope.launch {
            syncSettings.disable()
            _syncUi.value = SyncUi(state = SyncState.Disabled)
            _messages.send(UiMessage(R.string.sync_disconnected))
        }
    }

    private fun observeSyncConfig() {
        viewModelScope.launch {
            syncSettings.configFlow.collect { config ->
                _syncUi.update { current ->
                    current.copy(
                        state = when {
                            !config.enabled -> SyncState.Disabled
                            current.state == SyncState.Syncing -> SyncState.Syncing
                            current.state == SyncState.Offline -> SyncState.Offline
                            else -> SyncState.Idle
                        },
                        code = config.code.takeIf { it.isNotBlank() },
                        serverUrl = config.serverUrl,
                        lastSyncedAt = config.lastSyncedAt,
                    )
                }
            }
        }
    }

    private fun startSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                val config = syncSettings.config()
                if (config.enabled) performSync(silent = true)
                delay(if (config.enabled) SYNC_INTERVAL_MS else 30_000L)
            }
        }
    }

    /** Nudges sync shortly after a local edit instead of waiting for the next tick. */
    private fun scheduleSync() {
        viewModelScope.launch {
            delay(600)
            performSync(silent = true)
        }
    }

    private suspend fun performSync(silent: Boolean) {
        if (syncing) return
        val config = syncSettings.config()
        if (!config.enabled) return

        syncing = true
        _syncUi.update { it.copy(state = SyncState.Syncing) }
        try {
            syncEngine.sync()
            val refreshed = syncSettings.config()
            _syncUi.value = SyncUi(
                state = SyncState.Idle,
                code = refreshed.code,
                serverUrl = refreshed.serverUrl,
                lastSyncedAt = refreshed.lastSyncedAt,
            )
        } catch (error: Exception) {
            _syncUi.update { it.copy(state = SyncState.Offline) }
            if (!silent) _messages.send(UiMessage(R.string.sync_failed))
        } finally {
            syncing = false
        }
    }

    private fun normalizeServerUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "http://$value"
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

    private fun isDayFirst(): Boolean {
        val country = Locale.getDefault().country.uppercase(Locale.UK)
        return country !in MONTH_FIRST_COUNTRIES
    }

    private fun displayNameFor(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.none { it.isLowerCase() }) {
            trimmed.lowercase(Locale.getDefault())
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.getDefault()) } }
        } else {
            trimmed
        }
    }

    companion object {
        private const val SYNC_INTERVAL_MS = 6_000L
        private val MONTH_FIRST_COUNTRIES = setOf("US", "PH", "MX", "CA")

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ListViewModel(
                        repository = container.items,
                        storeRepository = container.stores,
                        receipts = container.receipts,
                        updates = container.updates,
                        settings = container.settings,
                        syncEngine = container.sync,
                        syncSettings = container.syncSettings,
                        ocr = container.ocr,
                    ) as T
            }
    }
}
