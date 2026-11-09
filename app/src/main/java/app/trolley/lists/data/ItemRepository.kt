package app.trolley.lists.data

import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for list mutations. The UI never talks to Room directly, which keeps
 * a future sync layer (shared lists) a drop-in replacement here.
 */
class ItemRepository(private val dao: ItemDao) {

    fun observeActive(): Flow<List<Item>> = dao.observeActive(Item.DEFAULT_LIST_ID)

    fun observeHistory(): Flow<List<Item>> = dao.observeHistory(Item.DEFAULT_LIST_ID)

    suspend fun add(name: String) {
        val now = System.currentTimeMillis()
        dao.upsert(Item(name = name, createdAt = now, updatedAt = now))
    }

    suspend fun complete(item: Item) = dao.complete(item.id, System.currentTimeMillis())

    suspend fun restore(item: Item) = dao.restore(item.id, System.currentTimeMillis())

    suspend fun rename(item: Item, name: String) = dao.rename(item.id, name, System.currentTimeMillis())

    suspend fun delete(item: Item) = dao.deleteById(item.id)

    suspend fun restoreAll(items: List<Item>) = dao.upsertAll(items)

    /** Returns everything that was removed so the action can be undone. */
    suspend fun clearHistory(): List<Item> {
        val removed = dao.historySnapshot(Item.DEFAULT_LIST_ID)
        if (removed.isNotEmpty()) dao.deleteHistory(Item.DEFAULT_LIST_ID)
        return removed
    }
}
