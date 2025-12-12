package com.entrig.sdk.models

/**
 * Configuration for initializing the Entrig SDK.
 *
 * @property apiKey Your Entrig API key
 * @property handlePermissionAutomatically If true, SDK will automatically request notification permission on registration (Android 13+)
 */
data class EntrigConfig(
    val apiKey: String,
    val handlePermissionAutomatically: Boolean = true
) {
    init {
        require(apiKey.isNotEmpty()) { "API key cannot be empty" }
    }
}
