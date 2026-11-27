package com.ratsey.thelist.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratsey.thelist.BuildConfig
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.ui.components.AboutDialog
import com.ratsey.thelist.ui.components.AddItemBar
import com.ratsey.thelist.ui.components.BasketList
import com.ratsey.thelist.ui.components.BarcodeScannerScreen
import com.ratsey.thelist.ui.components.CheckoutSheet
import com.ratsey.thelist.ui.components.ConfirmDialog
import com.ratsey.thelist.ui.components.HomeHeader
import com.ratsey.thelist.ui.components.AddTypeDialog
import com.ratsey.thelist.ui.components.ItemActionsSheet
import com.ratsey.thelist.ui.components.PriceCompareSheet
import com.ratsey.thelist.ui.components.PlacementDialog
import com.ratsey.thelist.ui.components.PricePromptDialog
import com.ratsey.thelist.ui.components.RenameDialog
import com.ratsey.thelist.ui.components.ScannedProductSheet
import com.ratsey.thelist.ui.components.ShelfSuggestions
import com.ratsey.thelist.ui.components.ShelfTagScannerScreen
import com.ratsey.thelist.ui.components.SettingsDialog
import com.ratsey.thelist.ui.components.StoresSheet
import com.ratsey.thelist.ui.components.ShoppingList
import com.ratsey.thelist.ui.components.SyncDialog
import com.ratsey.thelist.ui.components.ShelfActionsSheet
import com.ratsey.thelist.ui.components.ShelfScreen
import com.ratsey.thelist.data.shelfMatches
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.ratsey.thelist.ui.components.VariantSheet

/** In the order a shop happens: what you keep, what you need, what you have picked up. */
private const val TAB_SHELF = 0
private const val TAB_TO_BUY = 1
private const val TAB_BASKET = 2

@Composable
fun TheListRoot(viewModel: ListViewModel) {
    val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val basketItems by viewModel.basketItems.collectAsStateWithLifecycle()
    val shelfItems by viewModel.shelfItems.collectAsStateWithLifecycle()
    val productDetail by viewModel.productDetail.collectAsStateWithLifecycle()
    val syncUi by viewModel.syncUi.collectAsStateWithLifecycle()
    val updateState by viewModel.update.collectAsStateWithLifecycle()
    val hintVisible by viewModel.hintVisible.collectAsStateWithLifecycle()
    val shelfHintVisible by viewModel.shelfHintVisible.collectAsStateWithLifecycle()
    val pricePrompt by viewModel.pricePrompt.collectAsStateWithLifecycle()
    val priceFor by viewModel.priceFor.collectAsStateWithLifecycle()
    val variantPrompt by viewModel.variantPrompt.collectAsStateWithLifecycle()
    val tagScanning by viewModel.tagScanning.collectAsStateWithLifecycle()
    val scannedTag by viewModel.scannedTag.collectAsStateWithLifecycle()
    val offerNoPricePrompt by viewModel.offerNoPricePrompt.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val storeNames = remember(stores) { stores.associate { it.id to it.name } }
    val currentStoreId by viewModel.currentStoreId.collectAsStateWithLifecycle()
    val priceSummaries by viewModel.priceSummaries.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val measureSystem by viewModel.measureSystem.collectAsStateWithLifecycle()
    val scanUi by viewModel.scanUi.collectAsStateWithLifecycle()
    val summaries = remember(priceSummaries) { priceSummaries.associateBy { it.key } }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val addFocusRequester = remember { FocusRequester() }

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_TO_BUY) }
    var draft by rememberSaveable { mutableStateOf("") }
    var actionSheetItem by remember { mutableStateOf<Item?>(null) }
    var shelfActionItem by remember { mutableStateOf<Item?>(null) }
    var renameTarget by remember { mutableStateOf<Item?>(null) }
    var addTypeTarget by remember { mutableStateOf<Item?>(null) }
    var showCheckout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showStores by remember { mutableStateOf(false) }
    var placementItem by remember { mutableStateOf<Item?>(null) }
    var shelfFilterOn by rememberSaveable { mutableStateOf(false) }
    // The shelf's filter is a field you ask for, so the rows keep its height the rest of the time.
    var shelfSearchOpen by rememberSaveable { mutableStateOf(false) }

    // A search nobody can see is a shelf that looks short: it closes with the tab, and with the
    // last row there was to search.
    LaunchedEffect(selectedTab, shelfItems.isEmpty()) {
        if (selectedTab != TAB_SHELF || shelfItems.isEmpty()) shelfSearchOpen = false
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

    val compareState by viewModel.compareState.collectAsStateWithLifecycle()

    compareState?.let { state ->
        val detail = state.detail
        PriceCompareSheet(
            item = state.item,
            detail = detail,
            onCapture = { price, unit -> viewModel.capturePrice(state.item.id, price, unit) },
            onOpenHistory = detail?.let { priced -> { viewModel.showPricesFor(priced.summary.key) } },
            onDismiss = viewModel::dismissCompare,
        )
    }

    productDetail?.let { detail ->
        ProductDetailScreen(
            detail = detail,
            measureSystem = measureSystem,
            onBack = viewModel::closeProduct,
        )
        return
    }

    // Reading a shelf tag covers the prompt it was opened from: what it reads lands back there.
    if (tagScanning) {
        ShelfTagScannerScreen(
            onTag = viewModel::tagScanned,
            onClose = viewModel::cancelTagScan,
        )
        return
    }

    // Scanning takes over the whole screen, as the camera does: one job at a time.
    if (scanUi is ScanUi.Camera || scanUi is ScanUi.Looking) {
        BarcodeScannerScreen(
            looking = scanUi is ScanUi.Looking,
            onBarcode = viewModel::onBarcodeScanned,
            onClose = viewModel::dismissScan,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedTab == TAB_TO_BUY) {
                // One column, not two siblings: the bottom bar lays its children on top of
                // each other, so a panel beside the field would be hidden underneath it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    // What is already on the shelf for this word, before the word becomes a second
                    // row. Typing always offers the closest few; the filter widens that and, with
                    // nothing typed, shows the shelf itself so last week's row is a swipe away.
                    ShelfSuggestions(
                        matches = remember(shelfItems, draft, shelfFilterOn) {
                            when {
                                draft.isNotBlank() ->
                                    shelfMatches(shelfItems, draft, if (shelfFilterOn) 8 else 3)

                                shelfFilterOn -> shelfItems.take(5)
                                else -> emptyList()
                            }
                        },
                        onRevive = viewModel::reviveFromShelf,
                    )
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
                        shelfFilterOn = shelfFilterOn,
                        shelfCount = shelfItems.size,
                        onToggleShelfFilter = { shelfFilterOn = !shelfFilterOn },
                        onScanCode = viewModel::openScanner,
                    )
                }
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
                    stores = stores,
                    currentStoreId = currentStoreId,
                    onSelectStore = viewModel::setCurrentStore,
                    searchOpen = shelfSearchOpen,
                    onToggleSearch = if (selectedTab == TAB_SHELF) {
                        { shelfSearchOpen = !shelfSearchOpen }
                    } else {
                        null
                    },
                    onTabSelected = { selectedTab = it },
                    onCheckForUpdates = { viewModel.checkForUpdates(userInitiated = true) },
                    onFinishShop = { showCheckout = true },
                    onOpenSettings = { showSettings = true },
                    onOpenStores = { showStores = true },
                    onAbout = { showAbout = true },
                    onStartUpdate = viewModel::startUpdate,
                    onInstallUpdate = viewModel::installUpdate,
                    onOpenInstallPermission = viewModel::openInstallPermissionSettings,
                    onDismissUpdate = viewModel::dismissUpdate,
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
                        TAB_SHELF -> ShelfScreen(
                            items = shelfItems,
                            storeNames = storeNames,
                            summaries = summaries,
                            currentStoreId = currentStoreId,
                            searchOpen = shelfSearchOpen,
                            hintVisible = shelfHintVisible,
                            onHideHint = { viewModel.dismissShelfHint(forGood = false) },
                            onNeverShowHint = { viewModel.dismissShelfHint(forGood = true) },
                            onAdd = viewModel::addFromShelf,
                            onOpenActions = { shelfActionItem = it },
                        )

                        TAB_BASKET -> BasketList(
                            items = basketItems,
                            storeNames = storeNames,
                            summaries = summaries,
                            currentStoreId = currentStoreId,
                            onToggle = viewModel::move,
                            onPutAway = viewModel::putAway,
                            onOpenDetails = { placementItem = it },
                            onOpenActions = { actionSheetItem = it },
                        )

                        else -> ShoppingList(
                            items = activeItems,
                            storeNames = storeNames,
                            summaries = summaries,
                            currentStoreId = currentStoreId,
                            showHint = hintVisible && basketItems.isEmpty() && activeItems.isNotEmpty(),
                            onToggle = viewModel::move,
                            onPutAway = viewModel::putAway,
                            onOpenPrices = { viewModel.comparePrices(it) },
                            onCompare = viewModel::comparePrices,
                            onOpenActions = { actionSheetItem = it },
                            onDismissHint = viewModel::dismissHint,
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
            onCompare = {
                actionSheetItem = null
                viewModel.comparePrices(item)
            },
            onToggle = {
                viewModel.move(item)
                actionSheetItem = null
            },
            onRename = {
                actionSheetItem = null
                renameTarget = item
            },
            onAddType = {
                actionSheetItem = null
                addTypeTarget = item
            },
            onPlacement = {
                actionSheetItem = null
                placementItem = item
            },
            onOpenPrices = {
                actionSheetItem = null
                viewModel.showPricesFor(item)
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

    shelfActionItem?.let { item ->
        ShelfActionsSheet(
            item = item,
            onDismiss = { shelfActionItem = null },
            onAdd = {
                viewModel.addFromShelf(item)
                shelfActionItem = null
            },
            onRename = {
                shelfActionItem = null
                renameTarget = item
            },
            onAddType = {
                shelfActionItem = null
                addTypeTarget = item
            },
            onPlacement = {
                shelfActionItem = null
                placementItem = item
            },
            onDelete = {
                viewModel.deleteFromShelf(item)
                shelfActionItem = null
            },
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { name, variant ->
                viewModel.rename(item, name, variant)
                renameTarget = null
            },
        )
    }

    addTypeTarget?.let { item ->
        AddTypeDialog(
            item = item,
            onDismiss = { addTypeTarget = null },
            onConfirm = { variant ->
                viewModel.addType(item, variant)
                addTypeTarget = null
            },
        )
    }

    placementItem?.let { item ->
        PlacementDialog(
            item = item,
            stores = stores,
            measureSystem = measureSystem,
            onDismiss = { placementItem = null },
            onSave = { name, storeId, newStoreName, variant, location, amount, price, priceUnit ->
                viewModel.setDetails(
                    item, name, storeId, newStoreName, variant, location, amount, price, priceUnit,
                )
                placementItem = null
            },
        )
    }

    // What the scan read: a product to confirm, or the empty form for a code that led nowhere.
    when (val scanned = scanUi) {
        is ScanUi.Found -> {
            val product = scanned.product
            ScannedProductSheet(
                initialName = product.name,
                initialAmount = product.quantity.orEmpty(),
                detail = listOfNotNull(product.brands, product.category)
                    .joinToString(" · ")
                    .ifEmpty { context.getString(R.string.scan_found_body) },
                notFound = false,
                onAdd = viewModel::addScanned,
                onScanAgain = viewModel::scanAgain,
                onDismiss = viewModel::dismissScan,
            )
        }

        is ScanUi.NotFound -> ScannedProductSheet(
            initialName = "",
            initialAmount = "",
            detail = context.getString(
                if (scanned.offline) R.string.scan_offline_body else R.string.scan_not_found_body,
                scanned.barcode,
            ),
            notFound = true,
            onAdd = viewModel::addScanned,
            onScanAgain = viewModel::scanAgain,
            onDismiss = viewModel::dismissScan,
        )

        else -> Unit
    }

    variantPrompt?.let { prompt ->
        VariantSheet(
            prompt = prompt,
            onChoose = { variant -> viewModel.addFromShelf(prompt.item, variant) },
            onDismiss = viewModel::dismissVariantPrompt,
        )
    }

    priceFor?.let { target ->
        PricePromptDialog(
            item = target.item,
            stores = stores,
            currentStoreId = currentStoreId,
            promptToken = target.token,
            scanned = scannedTag,
            onScanTag = viewModel::startTagScan,
            onSave = { price, location, pack, storeId ->
                viewModel.savePrice(target.item.id, price, location, pack, storeId)
            },
            onSkip = viewModel::skipPricePrompt,
        )
    }

    // Two skips running: offer to change the setting instead of interrupting every item.
    if (offerNoPricePrompt) {
        ConfirmDialog(
            title = stringResource(R.string.price_prompt_stop_title),
            body = stringResource(R.string.price_prompt_stop_body),
            confirmLabel = stringResource(R.string.price_prompt_stop_confirm),
            dismissLabel = stringResource(R.string.price_prompt_stop_dismiss),
            onConfirm = viewModel::turnOffPricePrompt,
            onDismiss = viewModel::keepPricePrompt,
        )
    }

    if (showCheckout) {
        CheckoutSheet(
            items = basketItems,
            storeName = storeNames[currentStoreId],
            onDismiss = { showCheckout = false },
            // Changing a shop or amount means the other sheet has to close first: two sheets
            // stacked would fight over the same drag, so this is a step out and back.
            onOpenDetails = { item ->
                showCheckout = false
                placementItem = item
            },
            onFinish = { prices ->
                showCheckout = false
                viewModel.finishShop(prices)
            },
        )
    }

    if (showStores) {
        StoresSheet(
            stores = stores,
            onDismiss = { showStores = false },
            onAdd = viewModel::addStore,
            onDelete = viewModel::deleteStore,
        )
    }

    if (showSettings) {
        SettingsDialog(
            pricePrompt = pricePrompt,
            onPricePromptChange = viewModel::setPricePrompt,
            currency = currency,
            onCurrencyChange = viewModel::setCurrency,
            measureSystem = measureSystem,
            onMeasureSystemChange = viewModel::setMeasureSystem,
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
