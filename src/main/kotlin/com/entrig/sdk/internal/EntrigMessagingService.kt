package com.entrig.sdk.internal

import android.content.Context
import android.util.Log
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.NotificationEvent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Internal Firebase Messaging Service for handling push notifications.
 * Not intended for direct use by SDK consumers.
 */
class EntrigMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "EntrigMessaging"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Extract data
        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val payload = data["payload"]?.let { jsonDecode(it) }?.toMutableMap() ?: mutableMapOf()
        payload.remove("title")
        payload.remove("body")
        val type = payload.remove("type") as? String

        val notificationEvent = NotificationEvent(
            title = notification?.title ?: "",
            body = notification?.body ?: "",
            type = type,
            data = payload
        )

        // Notify the SDK about the received notification
        Entrig.notifyNotificationReceived(notificationEvent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")

        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val userId = prefs.getString(KEY_USER_ID, null)

            if (userId == null) {
                Log.w(TAG, "onNewToken: No user ID found, skipping token refresh")
                return
            }

            // Re-register with the new token
            serviceScope.launch {
                try {
                    Entrig.refreshToken(applicationContext, userId, token)
                    Log.d(TAG, "Token refresh successful")
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh failed", e)
                }
            }
        } catch (e: Exception) {
            // Handle case where service is called before SDK initialization
            Log.w(TAG, "onNewToken: SDK not initialized yet, will retry after initialization", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No need to cancel the scope as it uses SupervisorJob
    }
}
