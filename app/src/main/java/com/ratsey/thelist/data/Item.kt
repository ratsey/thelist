package com.ratsey.thelist.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single list entry.
 *
 * [completedAt] is the only state switch: null means "still to buy", a timestamp means
 * "picked up" and places the item in History. [deletedAt] is a tombstone so deletions can
 * travel to other devices instead of silently reappearing on the next sync.
 */
@Entity(
    tableName = "items",
    indices = [Index(value = ["listId", "deletedAt", "completedAt"])],
)
data class Item(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = DEFAULT_LIST_ID,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    val isActive: Boolean get() = completedAt == null

    companion object {
        const val DEFAULT_LIST_ID = "groceries"
    }
}
