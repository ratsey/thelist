package com.ratsey.thelist.data

import androidx.room.withTransaction
import com.ratsey.thelist.sync.SyncPayloads
import kotlinx.coroutines.flow.Flow

/**
 * Shops, and the one case where removing one is not just a tombstone: items pointing at it have to
 * let go of the reference, or every row in the list would name a shop that no longer exists.
 */
class StoreRepository(private val db: AppDatabase) {

    private val stores = db.storeDao()
    private val items = db.itemDao()
    private val outbox = db.syncDao()

    fun observeAll(): Flow<List<Store>> = stores.observeAll(Item.DEFAULT_LIST_ID)

    suspend fun all(): List<Store> = stores.allNow(Item.DEFAULT_LIST_ID)

    /**
     * Adds a shop, or returns the one already called that. Returns null for a blank name. Callers
     * get the row back so "where it is" can save a new shop and use it in the same gesture.
     */
    suspend fun add(name: String): Store? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        stores.findByName(Item.DEFAULT_LIST_ID, ItemNames.key(trimmed))?.let { return it }

        val now = System.currentTimeMillis()
        val store = Store(name = trimmed, createdAt = now, updatedAt = now)
        db.withTransaction {
            stores.upsert(store)
            enqueue(store)
        }
        return store
    }

    suspend fun rename(store: Store, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == store.name) return

        val now = System.currentTimeMillis()
        val updated = store.copy(name = trimmed, updatedAt = now)
        db.withTransaction {
            stores.rename(store.id, trimmed, now)
            enqueue(updated)
        }
    }

    /**
     * Removes a shop. Its items keep their aisle note and lose only the dead reference: an aisle
     * number with no shop still tells you where to look, while a shop that is gone is just noise.
     */
    suspend fun delete(store: Store) {
        val now = System.currentTimeMillis()
        val updated = store.copy(deletedAt = now, updatedAt = now)
        val affected = items.withStore(store.id).map { it.copy(storeId = null, updatedAt = now) }

        db.withTransaction {
            stores.softDelete(store.id, now)
            enqueue(updated)
            if (affected.isNotEmpty()) {
                items.upsertAll(affected)
                enqueueItems(affected)
            }
        }
    }

    suspend fun enqueueAll(storesToSend: List<Store>) {
        if (storesToSend.isEmpty()) return
        outbox.enqueueAll(storesToSend.map { store -> op(store) })
    }

    private suspend fun enqueue(store: Store) {
        outbox.enqueue(op(store))
    }

    private fun op(store: Store) = SyncOp(
        entity = SyncOp.ENTITY_STORE,
        entityId = store.id,
        payload = SyncPayloads.storeJson(store),
        updatedAt = store.updatedAt,
    )

    private suspend fun enqueueItems(list: List<Item>) {
        outbox.enqueueAll(
            list.map { item ->
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
