package com.entrig.sdk.models

/**
 * Configuration for initializing the Entrig SDK.
 *
 * @property apiKey Your Entrig API key
 * @property handlePermission If true, SDK will automatically request notification permission on registration (Android 13+)
 * @property notificationChannelId Custom notification channel ID (default: "entrig_default")
 * @property notificationChannelName Custom notification channel name shown in settings (default: "General")
 * @property showForegroundNotification If true, notifications will be displayed when app is in foreground (default: true)
 */
data class EntrigConfig(
    val apiKey: String,
    val handlePermission: Boolean = true,
    val notificationChannelId: String = "entrig_default",
    val notificationChannelName: String = "General",
    val showForegroundNotification: Boolean = true
) {
    init {
        require(apiKey.isNotEmpty()) { "API key cannot be empty" }
    }
}
