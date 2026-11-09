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

    fun observeBasket(): Flow<List<Item>> = dao.observeBasket(Item.DEFAULT_LIST_ID)

    /** What an add actually did, so the UI can say "already on your list" instead of nothing. */
    enum class AddOutcome { Added, Readded, AlreadyListed }

    /**
     * Adds a name, or revives the existing row (from the basket or a tombstone) rather than
     * creating a second "Milk". Soft on purpose: a unique index would turn two phones adding
     * the same thing at once into a constraint failure during sync, and merging that is worse
     * than the rare duplicate it prevents.
     */
    suspend fun add(name: String): AddOutcome {
        val trimmed = name.trim()
        val now = System.currentTimeMillis()
        val existing = dao.findByName(Item.DEFAULT_LIST_ID, ItemNames.key(trimmed))

        if (existing != null && existing.completedAt == null) return AddOutcome.AlreadyListed

        if (existing != null) {
            val revived = existing.copy(completedAt = null, updatedAt = now)
            db.withTransaction {
                dao.upsert(revived)
                enqueue(revived)
            }
            return AddOutcome.Readded
        }

        val item = Item(name = trimmed, createdAt = now, updatedAt = now)
        db.withTransaction {
            dao.upsert(item)
            enqueue(item)
        }
        return AddOutcome.Added
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
    suspend fun clearBasket(): List<Item> {
        val removed = dao.basketSnapshot(Item.DEFAULT_LIST_ID)
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

    /**
     * What the shopper swiped while shopping, for receipt matching. Time-bounded because the
     * basket is long-lived: without the window, a receipt scanned today could claim an item
     * picked up last month instead of the one still on the list.
     */
    suspend fun recentlyPickedUp(now: Long = System.currentTimeMillis()): List<Item> =
        dao.pickedUpSince(Item.DEFAULT_LIST_ID, now - BASKET_WINDOW_MS)

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

    companion object {
        /** Long enough to cover shopping in the evening and scanning the receipt next morning. */
        private const val BASKET_WINDOW_MS = 36 * 60 * 60 * 1000L
    }
}
