package app.trolley.lists.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single list entry.
 *
 * [completedAt] is the only state switch: null means "still to buy", a timestamp means
 * "picked up" and places the item in History. [listId] and [updatedAt] exist so a second
 * list (and eventually a shared/synced list) can be added without a schema rewrite.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = DEFAULT_LIST_ID,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = completedAt == null

    companion object {
        const val DEFAULT_LIST_ID = "groceries"
    }
}
