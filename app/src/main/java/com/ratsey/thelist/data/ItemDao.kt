package com.ratsey.thelist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NULL ORDER BY createdAt ASC",
    )
    fun observeActive(listId: String): Flow<List<Item>>

    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NOT NULL ORDER BY completedAt DESC",
    )
    fun observeBasket(listId: String): Flow<List<Item>>

    /** The shelf: off the list but kept, alphabetical so it can be scanned. */
    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 0 " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    fun observeShelf(listId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: String): Item?

    /**
     * Duplicate check used before inserting. Matches on trimmed lowercase name, so the query
     * must stay aligned with [ItemNames.key] (which also collapses repeated spaces).
     */
    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL " +
            "AND lower(trim(name)) = :nameKey ORDER BY updatedAt DESC LIMIT 1",
    )
    suspend fun findByName(listId: String, nameKey: String): Item?

    @Query("SELECT * FROM items WHERE listId = :listId")
    suspend fun all(listId: String): List<Item>

    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NULL ORDER BY createdAt ASC",
    )
    suspend fun activeNow(listId: String): List<Item>

    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NOT NULL ORDER BY completedAt DESC",
    )
    suspend fun basketSnapshot(listId: String): List<Item>

    /** Items swiped since [since]. Bounded so an old pickup can't claim a fresh receipt line. */
    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NOT NULL AND completedAt >= :since ORDER BY completedAt DESC",
    )
    suspend fun pickedUpSince(listId: String, since: Long): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Item>)

    @Query("UPDATE items SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE items SET completedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun complete(id: String, now: Long)

    @Query("UPDATE items SET completedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    /** Takes an item off the list onto the shelf without deleting it. */
    @Query("UPDATE items SET onList = 0, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    /** Puts a shelf row back on the list, reusing it so its name and history stay put. */
    @Query("UPDATE items SET onList = 1, completedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun bringBack(id: String, now: Long)

    @Query("UPDATE items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
