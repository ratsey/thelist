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
        now: Long = System.currentTimeMillis(),
    ) {
        if (purchases.isEmpty()) return

        val receipt = Receipt(
            storeName = storeName?.trim().orEmpty(),
            purchasedAt = now,
            total = purchases.sumOf { it.price },
            currency = DEFAULT_CURRENCY,
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
        /** Rolls receipt lines into one summary per product, newest first. */
        fun summarise(rows: List<PriceRow>): List<PriceSummary> =
            rows.groupBy { it.productKey }
                .mapNotNull { (key, productRows) ->
                    val newest = productRows.maxByOrNull { it.purchasedAt } ?: return@mapNotNull null
                    PriceSummary(
                        productKey = key,
                        displayName = newest.displayName,
                        latestPrice = newest.unitPrice,
                        latestStore = newest.storeName,
                        latestAt = newest.purchasedAt,
                        lowestPrice = productRows.minOf { it.unitPrice },
                        averagePrice = productRows.map { it.unitPrice }.average(),
                        timesBought = productRows.size,
                        currency = newest.currency,
                    )
                }
                .sortedByDescending { it.latestAt }

        fun pointsFor(rows: List<PriceRow>, productKey: String): List<PricePoint> =
            rows.filter { it.productKey == productKey }
                .sortedBy { it.purchasedAt }
                .map { PricePoint(it.purchasedAt, it.unitPrice, it.storeName) }
    }
}

/**
 * A price typed in by hand: one item, one amount. The name travels with it because the price history
 * displays what was bought, and the row may have been renamed since.
 */
data class ManualPrice(val itemId: String, val name: String, val price: Double)
