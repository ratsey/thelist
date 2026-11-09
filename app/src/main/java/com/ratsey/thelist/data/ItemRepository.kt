package com.ratsey.thelist.data

import androidx.room.withTransaction
import com.ratsey.thelist.sync.SyncPayloads
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for list mutations. Every write also lands in the sync outbox, so a
 * shared list catches up as soon as the device is online.
 */
class ItemRepository(private val db: AppDatabase) {

    private val dao = db.itemDao()
    private val outbox = db.syncDao()

    fun observeActive(): Flow<List<Item>> = dao.observeActive(Item.DEFAULT_LIST_ID)

    fun observeHistory(): Flow<List<Item>> = dao.observeHistory(Item.DEFAULT_LIST_ID)

    suspend fun add(name: String) {
        val now = System.currentTimeMillis()
        val item = Item(name = name.trim(), createdAt = now, updatedAt = now)
        db.withTransaction {
            dao.upsert(item)
            enqueue(item)
        }
    }

    suspend fun complete(item: Item) {
        val now = System.currentTimeMillis()
        val updated = item.copy(completedAt = now, updatedAt = now)
        db.withTransaction {
            dao.complete(item.id, now)
            enqueue(updated)
        }
    }

    suspend fun restore(item: Item) {
        val now = System.currentTimeMillis()
        val updated = item.copy(completedAt = null, updatedAt = now)
        db.withTransaction {
            dao.restore(item.id, now)
            enqueue(updated)
        }
    }

    suspend fun rename(item: Item, name: String) {
        val now = System.currentTimeMillis()
        val updated = item.copy(name = name.trim(), updatedAt = now)
        db.withTransaction {
            dao.rename(item.id, name.trim(), now)
            enqueue(updated)
        }
    }

    suspend fun delete(item: Item) {
        val now = System.currentTimeMillis()
        val updated = item.copy(deletedAt = now, updatedAt = now)
        db.withTransaction {
            dao.softDelete(item.id, now)
            enqueue(updated)
        }
    }

    suspend fun restoreAll(items: List<Item>) {
        if (items.isEmpty()) return
        db.withTransaction {
            dao.upsertAll(items)
            enqueueAll(items)
        }
    }

    /** Returns everything that was removed so the action can be undone. */
    suspend fun clearHistory(): List<Item> {
        val removed = dao.historySnapshot(Item.DEFAULT_LIST_ID)
        if (removed.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val tombstones = removed.map { it.copy(deletedAt = now, updatedAt = now) }
        db.withTransaction {
            removed.forEach { dao.softDelete(it.id, now) }
            enqueueAll(tombstones)
        }
        return removed
    }

    suspend fun all(): List<Item> = dao.all(Item.DEFAULT_LIST_ID)

    /** Snapshot used to match receipt lines against what is still to buy. */
    suspend fun activeNow(): List<Item> = dao.activeNow(Item.DEFAULT_LIST_ID)

    suspend fun enqueue(item: Item) {
        outbox.enqueue(
            SyncOp(
                entity = SyncOp.ENTITY_ITEM,
                entityId = item.id,
                payload = SyncPayloads.itemJson(item),
                updatedAt = item.updatedAt,
            ),
        )
    }

    suspend fun enqueueAll(items: List<Item>) {
        if (items.isEmpty()) return
        outbox.enqueueAll(
            items.map { item ->
                SyncOp(
                    entity = SyncOp.ENTITY_ITEM,
                    entityId = item.id,
                    payload = SyncPayloads.itemJson(item),
                    updatedAt = item.updatedAt,
                )
            },
        )
    }
}
