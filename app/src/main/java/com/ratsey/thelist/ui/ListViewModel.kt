package com.ratsey.thelist.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ratsey.thelist.BuildConfig
import com.ratsey.thelist.R
import com.ratsey.thelist.data.AppContainer
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.ItemDetails
import com.ratsey.thelist.data.ItemRepository
import com.ratsey.thelist.data.DEFAULT_CURRENCY
import com.ratsey.thelist.data.ManualPrice
import com.ratsey.thelist.data.MeasureSystem
import com.ratsey.thelist.data.historyKey
import com.ratsey.thelist.data.PricePrompt
import com.ratsey.thelist.data.PriceRow
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.data.ReceiptRepository
import com.ratsey.thelist.data.SettingsStore
import com.ratsey.thelist.data.Store
import com.ratsey.thelist.data.StoreRepository
import com.ratsey.thelist.receipt.ItemMatcher
import com.ratsey.thelist.scan.Barcodes
import com.ratsey.thelist.scan.ProductLookup
import com.ratsey.thelist.scan.ScannedProduct
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * One request for a price: what to ask about, and a token identifying the asking. The prompt keys
 * its text on the token, so two requests about the same row cannot share a typed answer.
 */
data class PricePromptTarget(val item: Item, val token: Long)

/**
 * The scan-to-add flow. The camera is up, a read code is being looked up, or the prefill form
 * is open with what the database returned (or empty, because it returned nothing).
 */
sealed interface ScanUi {
    data object Camera : ScanUi
    data object Looking : ScanUi
    data class Found(val product: ScannedProduct) : ScanUi
    data class NotFound(
        val barcode: String,
        /** True when the database could not be reached, rather than not knowing the code. */
        val offline: Boolean = false,
    ) : ScanUi
}


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

/**
 * What the comparison popup is showing: the row, and its history if it has any. A row never priced
 * still opens, because the popup is also how a price gets recorded.
 */
data class CompareState(
    val item: Item,
    val detail: ProductDetail?,
)

class ListViewModel(
    private val repository: ItemRepository,
    private val storeRepository: StoreRepository,
    private val receipts: ReceiptRepository,
    private val updates: UpdateManager,
    private val settings: SettingsStore,
    private val syncEngine: SyncEngine,
    private val syncSettings: SyncSettingsRepository,
    private val productLookup: ProductLookup,
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

    /** When the app asks for a price: on the way into the basket, or all at once at the end. */
    val pricePrompt: StateFlow<PricePrompt> = settings.pricePrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PricePrompt.WhenAdding)

    /** The money prices are typed and shown in, from the region settings. */
    val currency: StateFlow<String> = settings.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_CURRENCY)

    fun setCurrency(symbol: String) {
        viewModelScope.launch { settings.setCurrency(symbol) }
    }

    /** Metric or imperial: which units are suggested for comparing prices. */
    val measureSystem: StateFlow<MeasureSystem> = settings.measureSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasureSystem.Imperial)

    fun setMeasureSystem(system: MeasureSystem) {
        viewModelScope.launch { settings.setMeasureSystem(system) }
    }

    /**
     * The open "what did it cost?" prompt, if any. The token makes each request its own: the same
     * row can be asked about twice, and the second time must not open holding the first answer.
     */
    private val _priceFor = MutableStateFlow<PricePromptTarget?>(null)
    val priceFor: StateFlow<PricePromptTarget?> = _priceFor.asStateFlow()

    private var pricePromptToken = 0L

    /**
     * Two skips in a row suggest the question is not wanted mid-shop. The offer is one-shot:
     * declining it starts a fresh run, so the user is asked at most every second skip.
     */
    private var priceSkipStreak = SkipStreak()

    private val _offerNoPricePrompt = MutableStateFlow(false)
    val offerNoPricePrompt: StateFlow<Boolean> = _offerNoPricePrompt.asStateFlow()

    /** Skip closes the question and counts towards the offer to stop asking altogether. */
    fun skipPricePrompt() {
        val outcome = PricePromptOffer.onSkip(priceSkipStreak)
        priceSkipStreak = outcome.streak
        _priceFor.value = null
        _offerNoPricePrompt.value = outcome.offerTurnOff
    }

    /** The "keep asking" answer to the offer: the run is over either way. */
    fun keepPricePrompt() {
        priceSkipStreak = PricePromptOffer.reset()
        _offerNoPricePrompt.value = false
    }

    /** The "stop asking" answer writes the same setting the dialog in Settings edits. */
    fun turnOffPricePrompt() {
        _offerNoPricePrompt.value = false
        setPricePrompt(PricePrompt.Off)
    }

    /** Which shop you are in, from the header pill. Null means everywhere at once. */
    val currentStoreId: StateFlow<String?> = settings.currentStoreId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCurrentStore(storeId: String?) {
        viewModelScope.launch { settings.setCurrentStore(storeId) }
    }

    private val priceRows: StateFlow<List<PriceRow>> = receipts.observePriceRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val priceSummaries: StateFlow<List<PriceSummary>> = priceRows
        .map { ReceiptRepository.summarise(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedProduct = MutableStateFlow<String?>(null)
    private val comparedItem = MutableStateFlow<Item?>(null)

    private fun detailFor(rows: List<PriceRow>, key: String?): ProductDetail? {
        if (key == null) return null
        val summary = ReceiptRepository.summarise(rows).firstOrNull { it.key == key } ?: return null
        return ProductDetail(
            summary = summary,
            points = ReceiptRepository.pointsFor(rows, key),
            purchases = rows.filter { historyKey(it.productKey, it.variant) == key }
                .sortedByDescending { it.purchasedAt },
        )
    }

    val productDetail: StateFlow<ProductDetail?> =
        combine(priceRows, selectedProduct) { rows, key -> detailFor(rows, key) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The popup's subject, kept apart from [selectedProduct] because that one is a whole screen.
     * The detail is null for something never priced — the popup then offers to record the first
     * price rather than refusing to open.
     */
    val compareState: StateFlow<CompareState?> =
        combine(comparedItem, priceRows) { item, rows ->
            if (item == null) return@combine null
            CompareState(
                item = item,
                detail = detailFor(rows, historyKey(ItemMatcher.productKey(item.name), item.variant)),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    private var lastUndo: Undo? = null
    private var syncing = false

    private sealed interface Undo {
        /** [restoreToList] is where the row has to end up: true puts it back on To buy. */
        data class Moved(val item: Item, val restoreToList: Boolean) : Undo
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

    /**
     * A shelf row swiped in from the suggestion strip. Same row, same history: this is the whole
     * point of offering it, rather than typing a name that would create a second one.
     */
    fun reviveFromShelf(item: Item) {
        viewModelScope.launch {
            repository.bringBack(item)
            lastUndo = null
            _messages.send(UiMessage(R.string.msg_readded))
            scheduleSync()
        }
    }

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

    // ------------------------------------------------------------------ scan to add

    private val _scanUi = MutableStateFlow<ScanUi?>(null)
    val scanUi: StateFlow<ScanUi?> = _scanUi.asStateFlow()

    fun openScanner() {
        _scanUi.value = ScanUi.Camera
    }

    fun dismissScan() {
        _scanUi.value = null
    }

    /** The prefill form's "scan again" for a code the database did not know. */
    fun scanAgain() {
        _scanUi.value = ScanUi.Camera
    }

    /**
     * One code, one lookup: the analyzer stops at the first retail barcode it reads, the digits
     * go to the product database, and whatever comes back — a product, an empty form, nothing —
     * opens as the prefill sheet.
     */
    fun onBarcodeScanned(raw: String) {
        val barcode = Barcodes.normalize(raw) ?: return
        if (_scanUi.value != ScanUi.Camera) return
        _scanUi.value = ScanUi.Looking
        viewModelScope.launch {
            _scanUi.value = try {
                productLookup.lookup(barcode)?.let(ScanUi::Found) ?: ScanUi.NotFound(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanUi.NotFound(barcode, offline = true)
            }
        }
    }

    /** Confirming the prefill form: the row goes on the list with the pack size it carried. */
    fun addScanned(name: String, amount: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (repository.addScanned(trimmed, amount?.trim()?.takeIf { it.isNotEmpty() })) {
                ItemRepository.AddOutcome.AlreadyListed ->
                    _messages.send(UiMessage(R.string.msg_already_listed))

                ItemRepository.AddOutcome.Readded ->
                    _messages.send(UiMessage(R.string.msg_readded))

                ItemRepository.AddOutcome.Added -> Unit
            }
            scheduleSync()
            _scanUi.value = null
        }
    }

    fun move(item: Item) {
        val toBasket = item.completedAt == null
        viewModelScope.launch {
            if (toBasket) repository.complete(item) else repository.restore(item)
            lastUndo = Undo.Moved(item, restoreToList = toBasket)
            _hintVisible.value = false
            // The price is known at the shelf, so "when adding" asks right here. A row that already
            // has one is not asked again: the prompt is a convenience, not a gate.
            if (toBasket && item.price == null && pricePrompt.value == PricePrompt.WhenAdding) {
                _offerNoPricePrompt.value = false
                _priceFor.value = PricePromptTarget(item, ++pricePromptToken)
            }
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

    fun rename(item: Item, rawName: String, variant: String? = null) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        if (name == item.name && variant?.trim().orEmpty() == item.variant.orEmpty()) return
        viewModelScope.launch {
            repository.rename(item, name, variant)
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

    fun deleteStore(store: Store) {
        viewModelScope.launch {
            storeRepository.delete(store)
            // Standing in a shop that no longer exists would silently hide every aisle.
            if (settings.currentStoreIdNow() == store.id) settings.setCurrentStore(null)
            _messages.send(UiMessage(R.string.msg_store_deleted))
            scheduleSync()
        }
    }

    /**
     * Saves what the shopper knows about a row: its product name, which shop it is bought in, where
     * in it, how much to buy and what it should cost. A shop typed straight into the sheet is created first, so
     * "Walmart, aisle A6, 2 L, $3.50" is one gesture instead of a detour through a separate screen
     * to set the shop up before it can be chosen.
     */
    fun setDetails(
        item: Item,
        name: String,
        storeId: String?,
        newStoreName: String?,
        variant: String,
        location: String,
        amount: String?,
        price: Double?,
        priceUnit: String?,
    ) {
        viewModelScope.launch {
            val created = newStoreName?.let { storeRepository.add(it) }
            val target = created?.id ?: storeId
            val finalName = ItemDetails.nameOrExisting(item, name)
            repository.setDetails(item, target, location, variant, amount, price, priceUnit, finalName)
            // A price typed here is a price known, not only an intention: a row showing "$3.50"
            // whose history has never heard of it is a row that lies. Only the first one becomes
            // history — later plans stay plans, and the till records what was actually paid.
            val key = historyKey(ItemMatcher.productKey(finalName), variant.trim().ifBlank { null })
            if (price != null && priceRows.value.none { historyKey(it.productKey, it.variant) == key }) {
                receipts.recordPrices(
                    storeName = created?.name ?: storeNameFor(item),
                    currency = currency.value,
                    purchases = listOf(
                        ManualPrice(
                            itemId = item.id,
                            name = finalName,
                            price = price,
                            variant = variant.trim().ifBlank { null },
                            priceUnit = priceUnit,
                        ),
                    ),
                )
            }
            val cleared = finalName == item.name && target == null && location.isBlank() &&
                variant.isBlank() && amount.isNullOrBlank() && price == null && priceUnit.isNullOrBlank()
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
                    if (undo.restoreToList) repository.restore(undo.item) else repository.complete(undo.item)

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

    fun setPricePrompt(prompt: PricePrompt) {
        priceSkipStreak = PricePromptOffer.reset()
        viewModelScope.launch { settings.setPricePrompt(prompt) }
    }

    // ------------------------------------------------------------------- prices

    /**
     * A price typed in for one row: written onto the item, then into the price history, so the row
     * says what you planned to spend and last/best keep learning from what you actually did.
     */
    fun savePrice(itemId: String, price: Double?) {
        priceSkipStreak = PricePromptOffer.reset()
        _priceFor.value = null
        viewModelScope.launch {
            applyPrice(itemId, price)
            scheduleSync()
        }
    }

    private suspend fun applyPrice(itemId: String, price: Double?) {
        val item = repository.byId(itemId) ?: return
        repository.setPrice(itemId, price)
        if (price == null) return
        receipts.recordPrices(
            storeName = storeNameFor(item),
            currency = currency.value,
            purchases = listOf(
                ManualPrice(
                    itemId = item.id,
                    name = item.name,
                    price = price,
                    variant = item.variant,
                    priceUnit = item.priceUnit,
                ),
            ),
        )
    }

    /**
     * The end of the shop: whatever was typed at the till is written to the rows and into the price
     * history, then the basket empties onto the shelf. Clearing without pricing is allowed — the
     * shop is over either way — so an unpriced basket still gets put away in one press.
     */
    fun finishShop(prices: Map<String, Double?>) {
        viewModelScope.launch {
            val basket = basketItems.value
            val paid = mutableListOf<ManualPrice>()
            basket.forEach { item ->
                val price = prices[item.id]
                if (price != item.price) repository.setPrice(item.id, price)
                if (price != null) {
                    paid.add(ManualPrice(item.id, item.name, price, item.variant, item.priceUnit))
                }
            }

            if (paid.isNotEmpty()) {
                val shop = stores.value.firstOrNull { it.id == currentStoreId.value }?.name
                    ?: basket.firstNotNullOfOrNull { storeNameFor(it) }
                receipts.recordPrices(shop, paid, currency = currency.value)
            }

            // After the prices, not before: the snapshot this returns is what undo puts back.
            val removed = repository.clearBasket()
            lastUndo = Undo.ClearedBasket(removed)
            _messages.send(UiMessage(R.string.msg_shop_finished, undoable = true))
            scheduleSync()
        }
    }

    /** The shop a price belongs to: the row's own, or the one picked in the header. */
    private fun storeNameFor(item: Item): String? =
        stores.value.firstOrNull { it.id == item.storeId }?.name
            ?: stores.value.firstOrNull { it.id == currentStoreId.value }?.name

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
            selectedProduct.value = historyKey(key, item.variant)
        } else {
            viewModelScope.launch { _messages.send(UiMessage(R.string.prices_none_for_item)) }
        }
    }

    /** The comparison popup for a row on the list, priced or not. */
    fun comparePrices(item: Item) {
        comparedItem.value = item
    }

    fun dismissCompare() {
        comparedItem.value = null
    }

    /** From the popup into the full history of the same thing. */
    fun showPricesFor(key: String) {
        comparedItem.value = null
        selectedProduct.value = key
    }

    /**
     * A price found while comparing: onto the row as what you plan to pay, and into the history at
     * the row's shop, which is what the comparison above it is built from.
     */
    fun capturePrice(itemId: String, price: Double, priceUnit: String?) {
        viewModelScope.launch {
            val item = repository.byId(itemId) ?: return@launch
            repository.setDetails(
                item = item,
                storeId = item.storeId,
                location = item.location.orEmpty(),
                variant = item.variant.orEmpty(),
                amount = item.amount,
                price = price,
                priceUnit = priceUnit,
            )
            receipts.recordPrices(
                storeName = storeNameFor(item),
                currency = currency.value,
                purchases = listOf(
                    ManualPrice(
                        itemId = item.id,
                        name = item.name,
                        price = price,
                        variant = item.variant,
                        priceUnit = priceUnit,
                    ),
                ),
            )
            scheduleSync()
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
                        productLookup = container.productLookup,
                    ) as T
            }
    }
}
