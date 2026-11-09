package app.trolley.lists.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE listId = :listId AND completedAt IS NULL ORDER BY createdAt ASC")
    fun observeActive(listId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE listId = :listId AND completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun observeHistory(listId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE listId = :listId AND completedAt IS NOT NULL ORDER BY completedAt DESC")
    suspend fun historySnapshot(listId: String): List<Item>

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

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM items WHERE listId = :listId AND completedAt IS NOT NULL")
    suspend fun deleteHistory(listId: String)
}
