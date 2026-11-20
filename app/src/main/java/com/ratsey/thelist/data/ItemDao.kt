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

    /**
     * Every live row, newest edit first. Used for duplicate checks: the product key includes the
     * type and parses a slash inside older names, which is not something SQL should be guessing
     * at. Personal lists are small enough that comparing in Kotlin is simpler than a hash column.
     */
    @Query("SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun live(listId: String): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: String): Item?

    @Query("SELECT * FROM items WHERE listId = :listId")
    suspend fun all(listId: String): List<Item>

    @Query(
        "SELECT * FROM items WHERE listId = :listId AND deletedAt IS NULL AND onList = 1 " +
            "AND completedAt IS NOT NULL ORDER BY completedAt DESC",
    )
    suspend fun basketSnapshot(listId: String): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Item>)

    /** The name and the type together: they are one decision, made in one dialog. */
    @Query("UPDATE items SET name = :name, variant = :variant, updatedAt = :now WHERE id = :id")
    suspend fun setNameAndVariant(id: String, name: String, variant: String?, now: Long)

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

    /**
     * Where a row lives, which version of it to get and what that costs: shop, aisle, type, amount,
     * price and what the price is per, all optional.
     */
    @Query(
        "UPDATE items SET name = :name, storeId = :storeId, location = :location, variant = :variant, " +
            "amount = :amount, price = :price, priceUnit = :priceUnit, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun setDetails(
        id: String,
        name: String,
        storeId: String?,
        location: String?,
        variant: String?,
        amount: String?,
        price: Double?,
        priceUnit: String?,
        now: Long,
    )

    /** What a row cost. Null clears it, which is why the other details are left untouched. */
    @Query("UPDATE items SET price = :price, updatedAt = :now WHERE id = :id")
    suspend fun setPrice(id: String, price: Double?, now: Long)

    /** Every row pointing at a shop, used when that shop is removed. */
    @Query("SELECT * FROM items WHERE storeId = :storeId")
    suspend fun withStore(storeId: String): List<Item>

    @Query("UPDATE items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
