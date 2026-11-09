package app.trolley.lists.data

import android.content.Context
import app.trolley.lists.receipt.ReceiptOcr
import app.trolley.lists.sync.SyncEngine
import app.trolley.lists.update.UpdateManager

/**
 * Hand-rolled dependency container — small enough that a DI framework would be noise.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.build(appContext)

    val settings = SettingsStore(appContext)
    val syncSettings = SyncSettingsRepository(appContext)
    val items = ItemRepository(database)
    val receipts = ReceiptRepository(database)
    val updates = UpdateManager(appContext, settings)
    val ocr = ReceiptOcr(appContext)
    val sync = SyncEngine(database, syncSettings, items, receipts)
}
