package com.ratsey.thelist.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One shopping trip: where, when and how much. Photos are optional. */
@Entity(
    tableName = "receipts",
    indices = [Index(value = ["listId", "purchasedAt"])],
)
data class Receipt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = Item.DEFAULT_LIST_ID,
    val storeName: String,
    val purchasedAt: Long,
    val total: Double? = null,
    val currency: String = DEFAULT_CURRENCY,
    /** Bare file name inside the app's `receipts` directory, never an absolute path: this row
     *  travels to other devices, where a local path would mean nothing. */
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)
