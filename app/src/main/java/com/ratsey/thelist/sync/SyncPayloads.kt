package com.ratsey.thelist.sync

import com.ratsey.thelist.data.DEFAULT_CURRENCY
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.PurchaseLine
import com.ratsey.thelist.data.Receipt
import com.ratsey.thelist.data.Store
import org.json.JSONObject

/** JSON payloads shared by the outbox, the network and the receiving device. */
object SyncPayloads {

    /**
     * Shape of every payload below. It is written into each row so the relay holds
     * self-describing data, and so a future change can be recognised instead of guessed at.
     * Bump only alongside a reader that still understands the previous value.
     */
    const val VERSION = 1

    fun itemJson(item: Item): String = JSONObject().apply {
        put("v", VERSION)
        put("id", item.id)
        put("listId", item.listId)
        put("name", item.name)
        put("createdAt", item.createdAt)
        put("completedAt", item.completedAt ?: JSONObject.NULL)
        put("updatedAt", item.updatedAt)
        put("deletedAt", item.deletedAt ?: JSONObject.NULL)
        // Additive: a payload without it means "on the list", which is what an older writer
        // can only have meant, so the version number stays where it is.
        put("onList", item.onList)
        // Additive, like onList: an older payload has neither key, which reads as "not said",
        // so the version number stays put and an old writer's rows stay understood.
        put("storeId", item.storeId ?: JSONObject.NULL)
        put("location", item.location ?: JSONObject.NULL)
        // Additive in the same way: absent means "not set", which is all an older writer can have
        // meant, so a payload from before amounts still reads correctly.
        put("amount", item.amount ?: JSONObject.NULL)
        put("price", item.price ?: JSONObject.NULL)
    }.toString()

    fun itemFromJson(payload: String): Item {
        val json = JSONObject(payload)
        return Item(
            id = json.getString("id"),
            listId = json.optString("listId", Item.DEFAULT_LIST_ID),
            name = json.optString("name"),
            createdAt = json.optLong("createdAt", 0L),
            completedAt = json.optLongOrNull("completedAt"),
            updatedAt = json.optLong("updatedAt", 0L),
            deletedAt = json.optLongOrNull("deletedAt"),
            onList = json.optBoolean("onList", true),
            storeId = json.optStringOrNull("storeId"),
            location = json.optStringOrNull("location"),
            amount = json.optStringOrNull("amount"),
            price = json.optDoubleOrNull("price"),
        )
    }

    fun storeJson(store: Store): String = JSONObject().apply {
        put("v", VERSION)
        put("id", store.id)
        put("listId", store.listId)
        put("name", store.name)
        put("createdAt", store.createdAt)
        put("updatedAt", store.updatedAt)
        put("deletedAt", store.deletedAt ?: JSONObject.NULL)
    }.toString()

    fun storeFromJson(payload: String): Store {
        val json = JSONObject(payload)
        return Store(
            id = json.getString("id"),
            listId = json.optString("listId", Item.DEFAULT_LIST_ID),
            name = json.optString("name"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            deletedAt = json.optLongOrNull("deletedAt"),
        )
    }

    fun receiptJson(receipt: Receipt): String = JSONObject().apply {
        put("v", VERSION)
        put("id", receipt.id)
        put("listId", receipt.listId)
        put("storeName", receipt.storeName)
        put("purchasedAt", receipt.purchasedAt)
        put("total", receipt.total ?: JSONObject.NULL)
        put("currency", receipt.currency)
        put("photoPath", receipt.photoPath ?: JSONObject.NULL)
        put("createdAt", receipt.createdAt)
        put("updatedAt", receipt.updatedAt)
        put("deletedAt", receipt.deletedAt ?: JSONObject.NULL)
    }.toString()

    fun receiptFromJson(payload: String): Receipt {
        val json = JSONObject(payload)
        return Receipt(
            id = json.getString("id"),
            listId = json.optString("listId", Item.DEFAULT_LIST_ID),
            storeName = json.optString("storeName"),
            purchasedAt = json.optLong("purchasedAt", 0L),
            total = json.optDoubleOrNull("total"),
            currency = json.optString("currency", DEFAULT_CURRENCY),
            photoPath = json.optStringOrNull("photoPath"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            deletedAt = json.optLongOrNull("deletedAt"),
        )
    }

    fun lineJson(line: PurchaseLine): String = JSONObject().apply {
        put("v", VERSION)
        put("id", line.id)
        put("listId", line.listId)
        put("receiptId", line.receiptId)
        put("productKey", line.productKey)
        put("displayName", line.displayName)
        put("sourceText", line.sourceText ?: JSONObject.NULL)
        put("quantity", line.quantity)
        put("unitPrice", line.unitPrice)
        put("lineTotal", line.lineTotal)
        put("matchedItemId", line.matchedItemId ?: JSONObject.NULL)
        put("createdAt", line.createdAt)
        put("updatedAt", line.updatedAt)
        put("deletedAt", line.deletedAt ?: JSONObject.NULL)
    }.toString()

    fun lineFromJson(payload: String): PurchaseLine {
        val json = JSONObject(payload)
        return PurchaseLine(
            id = json.getString("id"),
            listId = json.optString("listId", Item.DEFAULT_LIST_ID),
            receiptId = json.getString("receiptId"),
            productKey = json.optString("productKey"),
            displayName = json.optString("displayName"),
            sourceText = json.optStringOrNull("sourceText"),
            quantity = json.optDouble("quantity", 1.0),
            unitPrice = json.optDouble("unitPrice", 0.0),
            lineTotal = json.optDouble("lineTotal", 0.0),
            matchedItemId = json.optStringOrNull("matchedItemId"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            deletedAt = json.optLongOrNull("deletedAt"),
        )
    }

    /**
     * Last-write-wins: the later timestamp wins, and a tie goes to the incoming change so
     * that two devices converge instead of ping-ponging.
     */
    fun shouldApply(remoteUpdatedAt: Long, localUpdatedAt: Long?): Boolean =
        localUpdatedAt == null || remoteUpdatedAt >= localUpdatedAt
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
