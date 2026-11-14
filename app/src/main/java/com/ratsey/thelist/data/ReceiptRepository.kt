package com.ratsey.thelist.data

import androidx.room.withTransaction
import com.ratsey.thelist.receipt.ItemMatcher
import com.ratsey.thelist.sync.SyncPayloads
import kotlinx.coroutines.flow.Flow

/**
 * Receipts, their priced lines, and the price history derived from them.
 *
 * Price history is not stored as its own table: it is recomputed from the receipt lines, so
 * two devices that both buy milk cannot disagree about a cached "last price".
 */
class ReceiptRepository(private val db: AppDatabase) {

    private val receipts = db.receiptDao()
    private val lines = db.purchaseLineDao()
    private val items = db.itemDao()
    private val outbox = db.syncDao()

    fun observePriceRows(): Flow<List<PriceRow>> = receipts.observePriceRows(Item.DEFAULT_LIST_ID)

    fun observeReceipts(): Flow<List<Receipt>> = receipts.observeReceipts(Item.DEFAULT_LIST_ID)

    fun observeLines(receiptId: String): Flow<List<PurchaseLine>> =
        lines.observeForReceipt(Item.DEFAULT_LIST_ID, receiptId)

    suspend fun saveReceipt(
        receipt: Receipt,
        purchaseLines: List<PurchaseLine>,
        markBought: List<Item>,
        now: Long = System.currentTimeMillis(),
    ) {
        db.withTransaction {
            receipts.upsert(receipt)
            outbox.enqueue(
                SyncOp(
                    entity = SyncOp.ENTITY_RECEIPT,
                    entityId = receipt.id,
                    payload = SyncPayloads.receiptJson(receipt),
                    updatedAt = receipt.updatedAt,
                ),
            )

            purchaseLines.forEach { line ->
                lines.upsert(line)
                outbox.enqueue(
                    SyncOp(
                        entity = SyncOp.ENTITY_LINE,
                        entityId = line.id,
                        payload = SyncPayloads.lineJson(line),
                        updatedAt = line.updatedAt,
                    ),
                )
            }

            markBought.forEach { item ->
                val updated = item.copy(completedAt = now, updatedAt = now)
                items.complete(item.id, now)
                outbox.enqueue(
                    SyncOp(
                        entity = SyncOp.ENTITY_ITEM,
                        entityId = updated.id,
                        payload = SyncPayloads.itemJson(updated),
                        updatedAt = updated.updatedAt,
                    ),
                )
            }
        }
    }

    /**
     * Records prices the shopper typed in as one receipt per checkout, which is the shape the price
     * history already reads: summaries, the chart and the last/best on a row all keep working, and
     * a shared list syncs a typed price exactly as it synced a scanned one.
     */
    suspend fun recordPrices(
        storeName: String?,
        purchases: List<ManualPrice>,
        currency: String = DEFAULT_CURRENCY,
        now: Long = System.currentTimeMillis(),
    ) {
        if (purchases.isEmpty()) return

        val receipt = Receipt(
            storeName = storeName?.trim().orEmpty(),
            purchasedAt = now,
            // Deliberately no total: a price is a cost kept for next time, and a sum of rates in
            // different units would not be the money that left the account anyway.
            total = null,
            currency = currency,
            createdAt = now,
            updatedAt = now,
        )

        saveReceipt(
            receipt = receipt,
            purchaseLines = purchases.map { purchase ->
                PurchaseLine(
                    receiptId = receipt.id,
                    productKey = ItemMatcher.productKey(purchase.name),
                    displayName = purchase.name,
                    sourceText = null,
                    quantity = 1.0,
                    unitPrice = purchase.price,
                    lineTotal = purchase.price,
                    variant = purchase.variant,
                    priceUnit = purchase.priceUnit,
                    matchedItemId = purchase.itemId,
                    createdAt = now,
                    updatedAt = now,
                )
            },
            // A price typed in is a price already paid, but it does not move anything on the list:
            // finish-shop moves the whole basket, and swiping is the only way onto it.
            markBought = emptyList(),
            now = now,
        )
    }

    suspend fun allReceipts(): List<Receipt> = receipts.all(Item.DEFAULT_LIST_ID)

    suspend fun allLines(): List<PurchaseLine> = lines.all(Item.DEFAULT_LIST_ID)

    suspend fun enqueueAll(receiptList: List<Receipt>, lineList: List<PurchaseLine>) {
        db.withTransaction {
            outbox.enqueueAll(
                receiptList.map { receipt ->
                    SyncOp(
                        entity = SyncOp.ENTITY_RECEIPT,
                        entityId = receipt.id,
                        payload = SyncPayloads.receiptJson(receipt),
                        updatedAt = receipt.updatedAt,
                    )
                } + lineList.map { line ->
                    SyncOp(
                        entity = SyncOp.ENTITY_LINE,
                        entityId = line.id,
                        payload = SyncPayloads.lineJson(line),
                        updatedAt = line.updatedAt,
                    )
                },
            )
        }
    }

    companion object {
        /**
         * Rolls receipt lines into one summary per product *and type*, newest first.
         *
         * Prices are only compared when they are per the same measure: every observation is restated
         * in the newest one's unit (a pound as a kilo, 100 g as lb), so "best" cannot be won by
         * quoting something in a smaller unit. A history mixing kilos with litres reports the newest
         * observation on its own rather than inventing a comparison.
         */
        fun summarise(rows: List<PriceRow>): List<PriceSummary> =
            rows.groupBy { historyKey(it.productKey, it.variant) }
                .mapNotNull { (_, history) ->
                    val newest = history.maxByOrNull { it.purchasedAt } ?: return@mapNotNull null
                    val basis = parsePriceBasis(newest.priceUnit)
                    val comparable = history.mapNotNull { row ->
                        priceIn(row.unitPrice, parsePriceBasis(row.priceUnit), basis)
                            ?.let { row to it }
                    }
                    val figures = comparable.map { it.second }.ifEmpty { listOf(newest.unitPrice) }
                    val latest = comparable.maxByOrNull { it.first.purchasedAt }?.second
                        ?: newest.unitPrice
                    PriceSummary(
                        productKey = newest.productKey,
                        displayName = newest.displayName,
                        latestPrice = latest,
                        latestStore = newest.storeName,
                        latestAt = newest.purchasedAt,
                        lowestPrice = figures.min(),
                        averagePrice = figures.average(),
                        timesBought = history.size,
                        currency = newest.currency,
                        variant = newest.variant,
                        basis = basis,
                        unitLabel = newest.priceUnit?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
                .sortedByDescending { it.latestAt }

        fun pointsFor(rows: List<PriceRow>, key: String): List<PricePoint> =
            rows.filter { historyKey(it.productKey, it.variant) == key }
                .sortedBy { it.purchasedAt }
                .map {
                    PricePoint(
                        it.purchasedAt,
                        it.unitPrice,
                        it.storeName,
                        parsePriceBasis(it.priceUnit),
                    )
                }
    }
}

/**
 * A price typed in by hand: one item, one amount. The name travels with it because the price history
 * displays what was bought, and the row may have been renamed since.
 */
data class ManualPrice(
    val itemId: String,
    val name: String,
    val price: Double,
    /** Which version was bought, so its price lands in that type's history and not another's. */
    val variant: String? = null,
    /** What the price was per, so it can be compared with the same measure later. */
    val priceUnit: String? = null,
)
