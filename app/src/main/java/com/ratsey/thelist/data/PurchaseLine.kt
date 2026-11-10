package com.ratsey.thelist.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A priced line from a receipt.
 *
 * [productKey] is the normalised product name and is the identity used for price history,
 * so "Tesco Semi Skimmed Milk 2L" and "Semi Skimmed Milk" both roll up under `milk`.
 */
@Entity(
    tableName = "purchase_lines",
    indices = [
        Index(value = ["listId", "productKey"]),
        Index(value = ["receiptId"]),
    ],
)
data class PurchaseLine(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = Item.DEFAULT_LIST_ID,
    val receiptId: String,
    val productKey: String,
    val displayName: String,
    /** A line from a receipt scanned before the camera went, exactly as it was read: prices
     *  typed in by hand have none, and older rows keep the brand this preserved. */
    val sourceText: String? = null,
    val quantity: Double = 1.0,
    val unitPrice: Double,
    val lineTotal: Double,
    val matchedItemId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

/** The currency prices are shown in until a receipt says otherwise. */
const val DEFAULT_CURRENCY = "£"

/** Flattened receipt line used for price history queries. */
data class PriceRow(
    val productKey: String,
    val displayName: String,
    /** The line as the receipt printed it, so the brand survives into the history. */
    val sourceText: String? = null,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val storeName: String,
    val purchasedAt: Long,
    val currency: String,
    val receiptId: String,
)

/** One product rolled up across every receipt. */
data class PriceSummary(
    val productKey: String,
    val displayName: String,
    val latestPrice: Double,
    val latestStore: String,
    val latestAt: Long,
    val lowestPrice: Double,
    val averagePrice: Double,
    val timesBought: Int,
    val currency: String,
)

/** A single price observation for the chart. */
data class PricePoint(
    val purchasedAt: Long,
    val unitPrice: Double,
    val storeName: String,
)
