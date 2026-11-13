package com.shapks.biovault

import org.json.JSONObject
import java.util.UUID

data class SecretItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SecretItem {
            return SecretItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Untitled"),
                content = json.optString("content", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
