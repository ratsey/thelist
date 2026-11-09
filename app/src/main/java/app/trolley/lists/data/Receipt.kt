package app.trolley.lists.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** One shopping trip: where, when and how much. Photos are optional. */
@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = Item.DEFAULT_LIST_ID,
    val storeName: String,
    val purchasedAt: Long,
    val total: Double? = null,
    val currency: String = "£",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)
