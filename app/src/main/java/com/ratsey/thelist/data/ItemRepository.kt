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

    fun observeShelf(): Flow<List<Item>> = dao.observeShelf(Item.DEFAULT_LIST_ID)

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

        // A shelf row is off the list with no completion time, so the check needs both flags.
        if (existing != null && existing.onList && existing.completedAt == null) {
            return AddOutcome.AlreadyListed
        }

        if (existing != null) {
            val revived = existing.copy(onList = true, completedAt = null, updatedAt = now)
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

    /**
     * Empties the basket onto the shelf instead of deleting it: what you just bought is next
     * week's list, and the row already knows how you spell it and what it cost. Returns the rows
     * as they were, so the clear can be undone.
     */
    suspend fun clearBasket(): List<Item> {
        val bought = dao.basketSnapshot(Item.DEFAULT_LIST_ID)
        if (bought.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val archived = bought.map { it.copy(onList = false, updatedAt = now) }
        db.withTransaction {
            bought.forEach { dao.archive(it.id, now) }
            enqueueAll(archived)
        }
        return bought
    }

    /** Swipe left on either list: off the list, but the row keeps its name, prices and id. */
    suspend fun putAway(item: Item) {
        val now = System.currentTimeMillis()
        val updated = item.copy(onList = false, updatedAt = now)
        db.withTransaction {
            dao.archive(item.id, now)
            enqueue(updated)
        }
    }

    /**
     * Records where a row lives. The shop is an id so a rename reaches every row; the aisle is
     * free text because that is what it is on a sign. A blank aisle is stored as null so "cleared"
     * and "never said" look the same everywhere, including on the other phone.
     */
    suspend fun setPlacement(item: Item, storeId: String?, location: String?) {
        val now = System.currentTimeMillis()
        val trimmed = location?.trim()?.takeIf { it.isNotEmpty() }
        val updated = item.copy(storeId = storeId, location = trimmed, updatedAt = now)
        db.withTransaction {
            dao.setPlacement(item.id, storeId, trimmed, now)
            enqueue(updated)
        }
    }

    /** A shelf row tapped in the sheet: back on the list, to buy, same row. */
    suspend fun bringBack(item: Item) {
        val now = System.currentTimeMillis()
        val updated = item.copy(onList = true, completedAt = null, updatedAt = now)
        db.withTransaction {
            dao.bringBack(item.id, now)
            enqueue(updated)
        }
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
