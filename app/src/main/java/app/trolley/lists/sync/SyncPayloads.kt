package app.trolley.lists.sync

import app.trolley.lists.data.Item
import app.trolley.lists.data.PurchaseLine
import app.trolley.lists.data.Receipt
import org.json.JSONObject

/** JSON payloads shared by the outbox, the network and the receiving device. */
object SyncPayloads {

    fun itemJson(item: Item): String = JSONObject().apply {
        put("id", item.id)
        put("listId", item.listId)
        put("name", item.name)
        put("createdAt", item.createdAt)
        put("completedAt", item.completedAt ?: JSONObject.NULL)
        put("updatedAt", item.updatedAt)
        put("deletedAt", item.deletedAt ?: JSONObject.NULL)
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
        )
    }

    fun receiptJson(receipt: Receipt): String = JSONObject().apply {
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
            currency = json.optString("currency", "£"),
            photoPath = json.optStringOrNull("photoPath"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            deletedAt = json.optLongOrNull("deletedAt"),
        )
    }

    fun lineJson(line: PurchaseLine): String = JSONObject().apply {
        put("id", line.id)
        put("listId", line.listId)
        put("receiptId", line.receiptId)
        put("productKey", line.productKey)
        put("displayName", line.displayName)
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
