package com.ratsey.thelist.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratsey.thelist.BuildConfig
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.receipt.ReceiptPhotos
import com.ratsey.thelist.ui.components.AboutDialog
import com.ratsey.thelist.ui.components.AddItemBar
import com.ratsey.thelist.ui.components.BasketList
import com.ratsey.thelist.ui.components.HomeHeader
import com.ratsey.thelist.ui.components.ItemActionsSheet
import com.ratsey.thelist.ui.components.RenameDialog
import com.ratsey.thelist.ui.components.SettingsDialog
import com.ratsey.thelist.ui.components.ShoppingList
import com.ratsey.thelist.ui.components.SyncDialog
import com.ratsey.thelist.ui.components.ShelfScreen
import java.io.File

private const val TAB_TO_BUY = 0
private const val TAB_BASKET = 1
private const val TAB_SHELF = 2
private const val TAB_PRICES = 3

@Composable
fun TheListRoot(viewModel: ListViewModel) {
    val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val basketItems by viewModel.basketItems.collectAsStateWithLifecycle()
    val shelfItems by viewModel.shelfItems.collectAsStateWithLifecycle()
    val priceSummaries by viewModel.priceSummaries.collectAsStateWithLifecycle()
    val productDetail by viewModel.productDetail.collectAsStateWithLifecycle()
    val scanState by viewModel.scan.collectAsStateWithLifecycle()
    val syncUi by viewModel.syncUi.collectAsStateWithLifecycle()
    val updateState by viewModel.update.collectAsStateWithLifecycle()
    val hintVisible by viewModel.hintVisible.collectAsStateWithLifecycle()
    val scanMovesItems by viewModel.scanMovesItems.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val addFocusRequester = remember { FocusRequester() }

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_TO_BUY) }
    var draft by rememberSaveable { mutableStateOf("") }
    var actionSheetItem by remember { mutableStateOf<Item?>(null) }
    var renameTarget by remember { mutableStateOf<Item?>(null) }
    var confirmClearBasket by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (saved && path != null) {
            val file = File(path)
            viewModel.startScan(ReceiptPhotos.uriFor(context, file), file.path)
        } else if (path != null) {
            runCatching { File(path).delete() }
        }
    }

    val openCamera: () -> Unit = {
        val file = ReceiptPhotos.newPhotoFile(context)
        pendingPhotoPath = file.path
        runCatching { cameraLauncher.launch(ReceiptPhotos.uriFor(context, file)) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(message.textRes),
                actionLabel = if (message.undoable) context.getString(R.string.action_undo) else null,
                withDismissAction = !message.undoable,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    LaunchedEffect(scanState) {
        val failed = scanState as? ScanUi.Failed ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = context.getString(failed.messageRes),
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissScanError()
    }

    val review = scanState as? ScanUi.Review
    if (review != null) {
        ScanReviewScreen(
            draft = review.draft,
            onDraftChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelScan,
        )
        return
    }

    productDetail?.let { detail ->
        ProductDetailScreen(detail = detail, onBack = viewModel::closeProduct)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedTab == TAB_TO_BUY) {
                AddItemBar(
                    value = draft,
                    onValueChange = { draft = it },
                    onAdd = {
                        if (draft.isNotBlank()) {
                            viewModel.addItem(draft)
                            draft = ""
                            addFocusRequester.requestFocus()
                        }
                    },
                    focusRequester = addFocusRequester,
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                HomeHeader(
                    tab = selectedTab,
                    activeCount = activeItems.size,
                    basketCount = basketItems.size,
                    shelfCount = shelfItems.size,
                    updateState = updateState,
                    syncUi = syncUi,
                    onTabSelected = { selectedTab = it },
                    onCheckForUpdates = { viewModel.checkForUpdates(userInitiated = true) },
                    onClearBasket = { confirmClearBasket = true },
                    onOpenSettings = { showSettings = true },
                    onAbout = { showAbout = true },
                    onStartUpdate = viewModel::startUpdate,
                    onInstallUpdate = viewModel::installUpdate,
                    onOpenInstallPermission = viewModel::openInstallPermissionSettings,
                    onDismissUpdate = viewModel::dismissUpdate,
                    onScan = openCamera,
                    onOpenSync = { showSync = true },
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "screen",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { tab ->
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        TAB_TO_BUY -> ShoppingList(
                            items = activeItems,
                            showHint = hintVisible && basketItems.isEmpty() && activeItems.isNotEmpty(),
                            onToggle = viewModel::move,
                            onPutAway = viewModel::putAway,
                            onOpenActions = { actionSheetItem = it },
                            onDismissHint = viewModel::dismissHint,
                        )

                        TAB_BASKET -> BasketList(
                            items = basketItems,
                            onToggle = viewModel::move,
                            onPutAway = viewModel::putAway,
                            onOpenActions = { actionSheetItem = it },
                        )

                        TAB_SHELF -> ShelfScreen(
                            items = shelfItems,
                            onAdd = viewModel::addFromShelf,
                            onDelete = viewModel::deleteFromShelf,
                        )

                        else -> PricesScreen(
                            summaries = priceSummaries,
                            onOpen = viewModel::openProduct,
                        )
                    }
                }
            }
        }
    }

    actionSheetItem?.let { item ->
        ItemActionsSheet(
            item = item,
            onDismiss = { actionSheetItem = null },
            onToggle = {
                viewModel.move(item)
                actionSheetItem = null
            },
            onRename = {
                actionSheetItem = null
                renameTarget = item
            },
            onPutAway = {
                viewModel.putAway(item)
                actionSheetItem = null
            },
            onDelete = {
                viewModel.delete(item)
                actionSheetItem = null
            },
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.rename(item, name)
                renameTarget = null
            },
        )
    }

    if (confirmClearBasket) {
        AlertDialog(
            onDismissRequest = { confirmClearBasket = false },
            title = { Text(stringResource(R.string.clear_basket_title)) },
            text = { Text(stringResource(R.string.clear_basket_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearBasket = false
                        viewModel.clearBasket()
                    },
                ) {
                    Text(stringResource(R.string.clear_basket_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearBasket = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            scanMovesItems = scanMovesItems,
            onScanMovesChange = viewModel::setScanMovesItems,
            onDismiss = { showSettings = false },
        )
    }

    if (showSync) {
        SyncDialog(
            syncUi = syncUi,
            suggestedServer = BuildConfig.SYNC_SERVER_URL,
            onDismiss = { showSync = false },
            onCreate = { serverUrl, name -> viewModel.createSharedList(serverUrl, name) },
            onJoin = { serverUrl, code, name -> viewModel.joinSharedList(serverUrl, code, name) },
            onSyncNow = viewModel::syncNow,
            onDisconnect = {
                viewModel.disconnectSync()
                showSync = false
            },
        )
    }

    if (showAbout) {
        AboutDialog(
            version = viewModel.currentVersion,
            onDismiss = { showAbout = false },
        )
    }
}

