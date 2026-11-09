package com.ratsey.thelist.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small key/value store for update bookkeeping and the handful of user choices. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val lastCheck = longPreferencesKey("last_update_check")
        val pendingDownloadId = longPreferencesKey("pending_download_id")
        val pendingVersion = stringPreferencesKey("pending_download_version")
        val scanMovesItems = booleanPreferencesKey("scan_moves_items")
        val currentStoreId = stringPreferencesKey("current_store_id")
    }

    /**
     * Whether a scanned receipt should move the items it matched out of the list. On by default:
     * scanning after a shop is how the list clears itself.
     */
    val scanMovesItems: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.scanMovesItems] ?: true }

    suspend fun setScanMovesItems(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.scanMovesItems] = value }
    }

    /**
     * The shop the user is standing in, chosen from the header pill. Persisted, because the shop
     * is usually picked at the door and the app gets killed on the way round. Null means all shops.
     */
    val currentStoreId: Flow<String?> = context.settingsDataStore.data.map { it[Keys.currentStoreId] }

    suspend fun setCurrentStore(storeId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (storeId == null) {
                prefs.remove(Keys.currentStoreId)
            } else {
                prefs[Keys.currentStoreId] = storeId
            }
        }
    }

    suspend fun currentStoreIdNow(): String? = currentStoreId.first()

    val lastCheckAt: Flow<Long> = context.settingsDataStore.data.map { it[Keys.lastCheck] ?: 0L }

    suspend fun setLastCheck(at: Long) {
        context.settingsDataStore.edit { it[Keys.lastCheck] = at }
    }

    suspend fun setPendingDownload(id: Long, version: String) {
        context.settingsDataStore.edit {
            it[Keys.pendingDownloadId] = id
            it[Keys.pendingVersion] = version
        }
    }

    suspend fun pendingDownload(): Pair<Long, String>? {
        val prefs = context.settingsDataStore.data.first()
        val id = prefs[Keys.pendingDownloadId] ?: return null
        val version = prefs[Keys.pendingVersion] ?: return null
        return if (id > 0) id to version else null
    }

    suspend fun scanMovesItemsNow(): Boolean = scanMovesItems.first()

    suspend fun clearPendingDownload() {
        context.settingsDataStore.edit {
            it.remove(Keys.pendingDownloadId)
            it.remove(Keys.pendingVersion)
        }
    }
}
