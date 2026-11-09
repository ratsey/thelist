package app.trolley.lists.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID

/**
 * Outbox of local changes waiting to be sent to the shared list.
 *
 * The payload is the full entity as JSON, so the receiving device can apply it without a
 * second request and last-write-wins resolves conflicts on [updatedAt].
 */
@Entity(tableName = "sync_outbox")
data class SyncOp(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entity: String,
    val entityId: String,
    val payload: String,
    val updatedAt: Long,
    val queuedAt: Long = System.currentTimeMillis(),
    val sent: Boolean = false,
) {
    companion object {
        const val ENTITY_ITEM = "item"
        const val ENTITY_RECEIPT = "receipt"
        const val ENTITY_LINE = "line"
    }
}

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: SyncOp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(ops: List<SyncOp>)

    @Query("SELECT * FROM sync_outbox WHERE sent = 0 ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOp>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE sent = 0")
    suspend fun pendingCount(): Int

    @Query("UPDATE sync_outbox SET sent = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<String>)

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt DESC LIMIT 1")
    suspend fun lastOp(): SyncOp?

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
