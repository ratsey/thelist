package com.ratsey.thelist.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * A shop you buy in, so a row can say where the thing lives: coffee, Walmart, aisle A6.
 *
 * A table rather than a string on each item, because the same shop is typed once and then picked
 * from a list, and because two people sharing one list should see the same set of shops. [deletedAt]
 * is a tombstone like the items', so removing a shop travels to the other phones instead of being
 * helpfully re-added by the next sync.
 */
@Entity(tableName = "stores")
data class Store(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String = Item.DEFAULT_LIST_ID,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Dao
interface StoreDao {

    @Query(
        "SELECT * FROM stores WHERE listId = :listId AND deletedAt IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    fun observeAll(listId: String): Flow<List<Store>>

    @Query(
        "SELECT * FROM stores WHERE listId = :listId AND deletedAt IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun allNow(listId: String): List<Store>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun byId(id: String): Store?

    /** Same rule as items: trimmed and lowercased, so "Walmart" and "walmart" are one shop. */
    @Query(
        "SELECT * FROM stores WHERE listId = :listId AND deletedAt IS NULL " +
            "AND lower(trim(name)) = :nameKey LIMIT 1",
    )
    suspend fun findByName(listId: String, nameKey: String): Store?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(store: Store)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stores: List<Store>)

    @Query("UPDATE stores SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM stores")
    suspend fun deleteAll()
}
