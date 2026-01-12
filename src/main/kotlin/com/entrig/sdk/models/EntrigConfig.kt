package com.entrig.sdk.models

/**
 * Configuration for initializing the Entrig SDK.
 *
 * @property apiKey Your Entrig API key
 * @property handlePermission If true, SDK will automatically request notification permission on registration (Android 13+)
 * @property notificationChannelId Custom notification channel ID (default: "default")
 * @property notificationChannelName Custom notification channel name shown in settings (default: "General")
 */
data class EntrigConfig(
    val apiKey: String,
    val handlePermission: Boolean = true,
    val notificationChannelId: String = "default",
    val notificationChannelName: String = "General"
) {
    init {
        require(apiKey.isNotEmpty()) { "API key cannot be empty" }
    }
}
