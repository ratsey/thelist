package app.trolley.lists.sync

import app.trolley.lists.data.SyncOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ListCredentials(val code: String, val token: String, val name: String)

data class RemoteOp(
    val id: String,
    val entity: String,
    val entityId: String,
    val payload: String,
    val updatedAt: Long,
)

data class PullResponse(val seq: Long, val ops: List<RemoteOp>)

class SyncException(message: String, val status: Int = 0) : Exception(message)

/**
 * Minimal JSON-over-HTTP client for the Trolley sync server (see /server).
 * No auth provider, no SDK: a list code plus a per-member token.
 */
class SyncClient(private val baseUrl: String) {

    suspend fun createList(name: String, listName: String): ListCredentials = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/v1/lists",
            body = JSONObject().apply {
                put("memberName", name)
                put("listName", listName)
            },
        )
        ListCredentials(
            code = response.getString("code"),
            token = response.getString("token"),
            name = response.optString("listName", listName),
        )
    }

    suspend fun joinList(code: String, name: String): ListCredentials = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/v1/lists/${code.trim().uppercase()}/join",
            body = JSONObject().put("memberName", name),
        )
        ListCredentials(
            code = response.getString("code"),
            token = response.getString("token"),
            name = response.optString("listName", "Shared list"),
        )
    }

    suspend fun push(code: String, token: String, deviceId: String, ops: List<SyncOp>): Long =
        withContext(Dispatchers.IO) {
            if (ops.isEmpty()) return@withContext 0L
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put(
                    "ops",
                    JSONArray().apply {
                        ops.forEach { op ->
                            put(
                                JSONObject().apply {
                                    put("id", op.id)
                                    put("entity", op.entity)
                                    put("entityId", op.entityId)
                                    put("payload", op.payload)
                                    put("updatedAt", op.updatedAt)
                                },
                            )
                        }
                    },
                )
            }
            request(
                method = "POST",
                path = "/v1/lists/${code}/ops",
                token = token,
                body = payload,
            ).optLong("seq", 0L)
        }

    suspend fun pull(code: String, token: String, since: Long): PullResponse =
        withContext(Dispatchers.IO) {
            val response = request(
                method = "GET",
                path = "/v1/lists/${code}/ops?since=$since",
                token = token,
            )
            val array = response.optJSONArray("ops") ?: JSONArray()
            val ops = buildList {
                for (index in 0 until array.length()) {
                    val op = array.getJSONObject(index)
                    add(
                        RemoteOp(
                            id = op.getString("id"),
                            entity = op.getString("entity"),
                            entityId = op.getString("entityId"),
                            payload = op.getString("payload"),
                            updatedAt = op.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
            PullResponse(seq = response.optLong("seq", since), ops = ops)
        }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Trolley-Android")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Sync server returned $status"
                throw SyncException(message, status)
            }

            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
