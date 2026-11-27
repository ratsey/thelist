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

/**
 * When the app asks what something cost. [Off] leaves it to a long press, [WhenAdding] asks as the
 * item goes into the basket, and [AtCheckout] saves every question for the sheet that empties the
 * basket — one interruption instead of twenty.
 */
enum class PricePrompt(val stored: String) {
    Off("off"),
    WhenAdding("basket"),
    AtCheckout("checkout"),
    ;

    companion object {
        /** Anything unreadable (a fresh install, a hand-edited store) asks as you shop. */
        fun fromStored(value: String?): PricePrompt =
            entries.firstOrNull { it.stored == value } ?: WhenAdding
    }
}

/** Small key/value store for update bookkeeping and the handful of user choices. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val lastCheck = longPreferencesKey("last_update_check")
        val pendingDownloadId = longPreferencesKey("pending_download_id")
        val pendingVersion = stringPreferencesKey("pending_download_version")
        val currentStoreId = stringPreferencesKey("current_store_id")
        val pricePrompt = stringPreferencesKey("price_prompt")
        val currency = stringPreferencesKey("currency")
        val measureSystem = stringPreferencesKey("measure_system")
        val shelfHintDismissed = booleanPreferencesKey("shelf_hint_dismissed")
    }

    /**
     * The shelf's one-line help, once it has been read for good. A dismissed hint is a choice that
     * outlives one shopping trip; hiding it for now never reaches this.
     */
    val shelfHintDismissed: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.shelfHintDismissed] ?: false
    }

    suspend fun setShelfHintDismissed() {
        context.settingsDataStore.edit { it[Keys.shelfHintDismissed] = true }
    }

    /** Whether prices are asked for while shopping, and at which moment. */
    val pricePrompt: Flow<PricePrompt> =
        context.settingsDataStore.data.map { PricePrompt.fromStored(it[Keys.pricePrompt]) }

    suspend fun setPricePrompt(prompt: PricePrompt) {
        context.settingsDataStore.edit { it[Keys.pricePrompt] = prompt.stored }
    }

    suspend fun pricePromptNow(): PricePrompt = pricePrompt.first()

    /** The money prices are typed and shown in. Storage is raw; reading is where it is cleaned up. */
    val currency: Flow<String> = context.settingsDataStore.data.map {
        normaliseCurrency(it[Keys.currency])
    }

    suspend fun setCurrency(symbol: String) {
        context.settingsDataStore.edit { it[Keys.currency] = normaliseCurrency(symbol) }
    }

    /** Which measures the app offers: metric grams and litres, or imperial ounces and pounds. */
    val measureSystem: Flow<MeasureSystem> = context.settingsDataStore.data.map {
        MeasureSystem.fromStored(it[Keys.measureSystem])
    }

    suspend fun setMeasureSystem(system: MeasureSystem) {
        context.settingsDataStore.edit { it[Keys.measureSystem] = system.stored }
    }

    suspend fun currencyNow(): String = currency.first()

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

    suspend fun clearPendingDownload() {
        context.settingsDataStore.edit {
            it.remove(Keys.pendingDownloadId)
            it.remove(Keys.pendingVersion)
        }
    }
}
