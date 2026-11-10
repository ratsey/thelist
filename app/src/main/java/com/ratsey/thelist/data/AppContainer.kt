package com.ratsey.thelist.data

import android.content.Context
import com.ratsey.thelist.sync.SyncEngine
import com.ratsey.thelist.update.UpdateManager

/**
 * Hand-rolled dependency container — small enough that a DI framework would be noise.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.build(appContext)

    val settings = SettingsStore(appContext)
    val syncSettings = SyncSettingsRepository(appContext)
    val items = ItemRepository(database)
    val stores = StoreRepository(database)
    val receipts = ReceiptRepository(database)
    val updates = UpdateManager(appContext, settings)
    val sync = SyncEngine(database, syncSettings, items, stores, receipts)
}
