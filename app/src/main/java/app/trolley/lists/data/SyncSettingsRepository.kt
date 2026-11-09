package app.trolley.lists.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

data class SyncConfig(
    val serverUrl: String = "",
    val code: String = "",
    val token: String = "",
    val listName: String = "",
    val lastSeq: Long = 0L,
    val lastSyncedAt: Long = 0L,
) {
    val enabled: Boolean
        get() = serverUrl.startsWith("http") && code.isNotBlank() && token.isNotBlank()
}

/** Connection details for the shared list, kept out of the database. */
class SyncSettingsRepository(private val context: Context) {

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val code = stringPreferencesKey("list_code")
        val token = stringPreferencesKey("member_token")
        val listName = stringPreferencesKey("list_name")
        val lastSeq = longPreferencesKey("last_seq")
        val lastSyncedAt = longPreferencesKey("last_synced_at")
        val deviceId = stringPreferencesKey("device_id")
    }

    val configFlow: Flow<SyncConfig> = context.syncDataStore.data.map { prefs ->
        SyncConfig(
            serverUrl = prefs[Keys.serverUrl].orEmpty(),
            code = prefs[Keys.code].orEmpty(),
            token = prefs[Keys.token].orEmpty(),
            listName = prefs[Keys.listName].orEmpty(),
            lastSeq = prefs[Keys.lastSeq] ?: 0L,
            lastSyncedAt = prefs[Keys.lastSyncedAt] ?: 0L,
        )
    }

    suspend fun config(): SyncConfig = configFlow.first()

    suspend fun enable(serverUrl: String, code: String, token: String, listName: String) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.serverUrl] = serverUrl.trimEnd('/')
            prefs[Keys.code] = code
            prefs[Keys.token] = token
            prefs[Keys.listName] = listName
            prefs[Keys.lastSeq] = 0L
            prefs[Keys.lastSyncedAt] = 0L
        }
    }

    suspend fun disable() {
        context.syncDataStore.edit { prefs ->
            prefs.remove(Keys.serverUrl)
            prefs.remove(Keys.code)
            prefs.remove(Keys.token)
            prefs.remove(Keys.listName)
            prefs.remove(Keys.lastSeq)
            prefs.remove(Keys.lastSyncedAt)
        }
    }

    suspend fun recordSync(seq: Long, at: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.lastSeq] = seq
            prefs[Keys.lastSyncedAt] = at
        }
    }

    /** Stable per-install identity, used only to attribute sync traffic. */
    suspend fun deviceId(): String {
        val existing = context.syncDataStore.data.first()[Keys.deviceId]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.syncDataStore.edit { it[Keys.deviceId] = generated }
        return generated
    }
}
