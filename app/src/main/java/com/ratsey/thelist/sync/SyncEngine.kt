package com.ratsey.thelist.sync

import androidx.room.withTransaction
import com.ratsey.thelist.data.AppDatabase
import com.ratsey.thelist.data.ItemRepository
import com.ratsey.thelist.data.ReceiptRepository
import com.ratsey.thelist.data.StoreRepository
import com.ratsey.thelist.data.SyncOp
import com.ratsey.thelist.data.SyncSettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncOutcome(
    val pushed: Int = 0,
    val applied: Int = 0,
    val sequence: Long = 0,
)

/**
 * Two-way sync: push the outbox, then pull everything newer than the last seen sequence.
 *
 * Conflicts resolve last-write-wins on `updatedAt`, which is good enough for a shopping
 * list and, unlike a vector clock, needs no bookkeeping on the server.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val settings: SyncSettingsRepository,
    private val items: ItemRepository,
    private val stores: StoreRepository,
    private val receipts: ReceiptRepository,
) {

    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val config = settings.config()
        if (!config.enabled) return@withLock SyncOutcome()

        val client = SyncClient(config.serverUrl)
        val deviceId = settings.deviceId()

        // 1. Upload local changes.
        val pending = db.syncDao().pending(BATCH_SIZE)
        if (pending.isNotEmpty()) {
            client.push(config.code, config.token, deviceId, pending)
            db.syncDao().markSent(pending.map { it.id })
        }

        // 2. Download everyone else's.
        val pull = client.pull(config.code, config.token, config.lastSeq)
        var applied = 0
        pull.ops.forEach { op ->
            if (applyRemote(op)) applied++
        }

        settings.recordSync(pull.seq, System.currentTimeMillis())
        SyncOutcome(pushed = pending.size, applied = applied, sequence = pull.seq)
    }

    /** Queues everything that already exists locally, used when turning on sharing. */
    suspend fun enqueueSnapshot() {
        items.enqueueAll(items.all())
        stores.enqueueAll(stores.all())
        receipts.enqueueAll(receipts.allReceipts(), receipts.allLines())
    }

    /** Wipes local rows and reloads everything from the server. */
    suspend fun replaceWithRemote() {
        db.withTransaction {
            db.purchaseLineDao().deleteAll()
            db.receiptDao().deleteAll()
            db.itemDao().deleteAll()
            db.storeDao().deleteAll()
            db.syncDao().deleteAll()
        }

        val config = settings.config()
        if (!config.enabled) return

        val client = SyncClient(config.serverUrl)
        val pull = client.pull(config.code, config.token, 0L)
        pull.ops.forEach { applyRemote(it) }
        settings.recordSync(pull.seq, System.currentTimeMillis())
    }

    private suspend fun applyRemote(op: RemoteOp): Boolean = when (op.entity) {
        SyncOp.ENTITY_ITEM -> {
            val remote = SyncPayloads.itemFromJson(op.payload)
            val local = db.itemDao().byId(remote.id)
            if (SyncPayloads.shouldApply(remote.updatedAt, local?.updatedAt)) {
                db.itemDao().upsert(remote)
                true
            } else {
                false
            }
        }

        SyncOp.ENTITY_STORE -> {
            val remote = SyncPayloads.storeFromJson(op.payload)
            val local = db.storeDao().byId(remote.id)
            if (SyncPayloads.shouldApply(remote.updatedAt, local?.updatedAt)) {
                db.storeDao().upsert(remote)
                true
            } else {
                false
            }
        }

        SyncOp.ENTITY_RECEIPT -> {
            val remote = SyncPayloads.receiptFromJson(op.payload)
            val local = db.receiptDao().byId(remote.id)
            if (SyncPayloads.shouldApply(remote.updatedAt, local?.updatedAt)) {
                db.receiptDao().upsert(remote)
                true
            } else {
                false
            }
        }

        SyncOp.ENTITY_LINE -> {
            val remote = SyncPayloads.lineFromJson(op.payload)
            val local = db.purchaseLineDao().byId(remote.id)
            if (SyncPayloads.shouldApply(remote.updatedAt, local?.updatedAt)) {
                db.purchaseLineDao().upsert(remote)
                true
            } else {
                false
            }
        }

        else -> false
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
