package com.entrig.sdk.internal

import org.json.JSONObject

internal const val PREFS_NAME = "com.entrig.sdk.prefs"
internal const val KEY_REGISTRATION_ID = "entrig_registration_id"
internal const val KEY_USER_ID = "entrig_user_id"
internal const val KEY_FCM_TOKEN = "entrig_fcm_token"

internal fun jsonDecode(value: String): MutableMap<String, Any?> {
    val jsonObject = JSONObject(value)
    return jsonObjectToMap(jsonObject)
}

private fun jsonObjectToMap(jsonObject: JSONObject): MutableMap<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    jsonObject.keys().forEach { key ->
        map[key] = jsonToNative(jsonObject.get(key))
    }
    return map
}

private fun jsonToNative(value: Any?): Any? {
    return when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is org.json.JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) {
                list.add(jsonToNative(value.get(i)))
            }
            list
        }
        JSONObject.NULL -> null
        else -> value
    }
}
