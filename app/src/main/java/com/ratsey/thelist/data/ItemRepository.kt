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

    /**
     * A scanned product adds like any other name, but the pack size off the label ("400 g")
     * comes with it into the row's amount, so the shopper does not retype what the code said.
     */
    suspend fun addScanned(name: String, amount: String?): AddOutcome {
        val outcome = add(name)
        if (outcome == AddOutcome.AlreadyListed || amount.isNullOrBlank()) return outcome

        val now = System.currentTimeMillis()
        val trimmedAmount = amount.trim()
        val item = dao.findByName(Item.DEFAULT_LIST_ID, ItemNames.key(name.trim()))
            ?: return outcome
        val updated = item.copy(amount = trimmedAmount, updatedAt = now)
        db.withTransaction {
            dao.setDetails(
                item.id,
                item.storeId,
                item.location,
                item.variant,
                trimmedAmount,
                item.price,
                item.priceUnit,
                now,
            )
            enqueue(updated)
        }
        return outcome
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

    suspend fun rename(item: Item, name: String, variant: String?) {
        val now = System.currentTimeMillis()
        val trimmedName = name.trim()
        val trimmedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }
        val updated = item.copy(name = trimmedName, variant = trimmedVariant, updatedAt = now)
        db.withTransaction {
            dao.setNameAndVariant(item.id, trimmedName, trimmedVariant, now)
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
     * Records the details a shopper sets on a row: the shop and aisle it lives in, which version of
     * it to get, how much to buy and what it should cost. The shop is an id so a rename reaches
     * every row; the aisle, the type and the amount are free text because that is what they are on
     * a sign and in a mouth. Blank text is stored as null so "cleared" and "never said" look the
     * same everywhere, including on the other phone.
     */
    suspend fun setDetails(
        item: Item,
        storeId: String?,
        location: String?,
        variant: String?,
        amount: String?,
        price: Double?,
        priceUnit: String?,
    ) {
        val now = System.currentTimeMillis()
        val trimmedLocation = location?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedAmount = amount?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedUnit = priceUnit?.trim()?.takeIf { it.isNotEmpty() }
        val updated = item.copy(
            storeId = storeId,
            location = trimmedLocation,
            variant = trimmedVariant,
            amount = trimmedAmount,
            price = price,
            priceUnit = trimmedUnit,
            updatedAt = now,
        )
        db.withTransaction {
            dao.setDetails(
                item.id, storeId, trimmedLocation, trimmedVariant, trimmedAmount, price,
                trimmedUnit, now,
            )
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

    /**
     * What a row cost, as typed in while shopping. The price is written straight to the row so the
     * list, the shelf and the other phone all agree, and only that one column is touched: the row
     * can be in the basket by the time this runs, and a whole-row write from a stale copy would
     * quietly put it back on the list.
     */
    suspend fun setPrice(itemId: String, price: Double?) {
        val current = dao.byId(itemId) ?: return
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setPrice(itemId, price, now)
            enqueue(current.copy(price = price, updatedAt = now))
        }
    }

    suspend fun byId(itemId: String): Item? = dao.byId(itemId)

    suspend fun all(): List<Item> = dao.all(Item.DEFAULT_LIST_ID)

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
