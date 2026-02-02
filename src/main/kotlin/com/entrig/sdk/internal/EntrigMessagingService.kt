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
        // Extract data from data-only message
        val data = remoteMessage.data

        // Get title and body directly from data map (data-only message)
        val title = data["title"] ?: ""
        val body = data["body"] ?: ""

        // Parse payload JSON for additional data
        val payload = data["payload"]?.let { jsonDecode(it) }?.toMutableMap() ?: mutableMapOf()
        payload.remove("title")
        payload.remove("body")
        val type = payload.remove("type") as? String
        val deliveryId = payload.remove("delivery_id") as? String

        val notificationEvent = NotificationEvent(
            title = title,
            body = body,
            type = type,
            deliveryId = deliveryId,
            data = payload
        )

        // Show notification based on foreground state and config
        val isInForeground = Entrig.isAppInForeground
        val showForegroundNotification = Entrig.config?.showForegroundNotification ?: true

        if (!isInForeground || showForegroundNotification) {
            // Show notification if app is in background/terminated OR if foreground notifications are enabled
            NotificationHelper.showNotification(this, remoteMessage.messageId, notificationEvent, data)
        }

        // Report "delivered" status to server
        if (deliveryId != null) {
            serviceScope.launch {
                try {
                    Entrig.reportDeliveryStatus(deliveryId, "delivered")
                    Log.d(TAG, "Delivery status reported: $deliveryId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to report delivery status: $deliveryId", e)
                }
            }
        }

        // Notify the SDK about the received notification (only when app is in foreground)
        if (isInForeground) {
            Entrig.notifyNotificationReceived(notificationEvent)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val userId = prefs.getString(KEY_USER_ID, null)

            if (userId == null) {
                return
            }

            // Re-register with the new token
            serviceScope.launch {
                try {
                    Entrig.refreshToken(applicationContext, userId, token)
                    Log.d(TAG, "FCM token refreshed")
                } catch (e: Exception) {
                    Log.e(TAG, "FCM token refresh failed", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh skipped: SDK not initialized")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No need to cancel the scope as it uses SupervisorJob
    }
}
