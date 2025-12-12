package com.entrig.sdk.models

import org.json.JSONObject

/**
 * Represents a notification event received from Entrig.
 *
 * @property title The notification title
 * @property body The notification body
 * @property type Optional notification type for custom handling
 * @property data Additional custom data payload
 */
data class NotificationEvent(
    val title: String?,
    val body: String?,
    val type: String?,
    val data: Map<String, Any?>?
) {
    /**
     * Converts the notification event to a map representation.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "title" to title,
            "body" to body,
            "type" to type,
            "data" to data
        )
    }

    companion object {
        /**
         * Creates a NotificationEvent from a JSON object.
         */
        fun fromJson(json: JSONObject): NotificationEvent {
            val title = json.optString("title", "")
            val body = json.optString("body", "")

            val data = mutableMapOf<String, Any?>()
            val dataObject = json.optJSONObject("data")
            var type: String? = null

            if (dataObject != null) {
                val keys = dataObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "type") {
                        type = dataObject.optString(key)
                    } else {
                        data[key] = dataObject.get(key)
                    }
                }
            }

            return NotificationEvent(title, body, type, data)
        }
    }
}
