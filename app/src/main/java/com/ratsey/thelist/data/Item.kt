package com.ratsey.thelist.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single list entry, in one of three places:
 *
 * - on the list, [completedAt] null: still to buy;
 * - on the list, [completedAt] set: picked up, waiting in the basket;
 * - off the list, [onList] false: on the **shelf**, kept but not being bought now, so it can go
 *   back on the list in one tap instead of being retyped.
 *
 * One flag plus one timestamp rather than three tables, so moving between the states is a single
 * row update that syncs like any other edit. [deletedAt] is a tombstone so deletions travel to
 * other devices instead of silently reappearing on the next sync.
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
    /**
     * True while the item belongs to the shopping list; false once it has been put away, whether
     * by swiping it left or by clearing the basket. Defaulted in the schema so the v2 → v3
     * migration is one `ALTER TABLE` and existing rows stay on the list.
     */
    @ColumnInfo(defaultValue = "1") val onList: Boolean = true,
    /**
     * The shop this row is bought in ([Store.id]) and the aisle or shelf inside it, e.g. "A6".
     *
     * Both are nullable because a list written in the kitchen knows neither, and because "not said
     * yet" is not the same as "no shop". [location] is free text on purpose: aisles are numbered,
     * lettered, called things ("the freezer") and, in a corner shop, do not exist at all.
     */
    val storeId: String? = null,
    val location: String? = null,
) {
    val isActive: Boolean get() = onList && completedAt == null

    companion object {
        const val DEFAULT_LIST_ID = "groceries"
    }
}
