package app.trolley.lists.data

import android.content.Context
import app.trolley.lists.update.UpdateManager

/**
 * Hand-rolled dependency container — small enough that a DI framework would be noise.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)
    val repository = ItemRepository(database.itemDao())
    val settings = SettingsStore(context)
    val updates = UpdateManager(context, settings)
}
