package com.entrig.sdk.internal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.NotificationEvent

internal object NotificationHelper {

    private const val TAG = "EntrigNotification"
    private const val DEFAULT_CHANNEL_ID = "default"
    private const val DEFAULT_CHANNEL_NAME = "General"
    private const val FCM_ICON_META_KEY = "com.google.firebase.messaging.default_notification_icon"

    fun showNotification(
        context: Context,
        messageId: String?,
        notification: NotificationEvent,
        data: Map<String, String>
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Get config values or use defaults
        val config = Entrig.config
        val channelId = config?.notificationChannelId ?: DEFAULT_CHANNEL_ID
        val channelName = config?.notificationChannelName ?: DEFAULT_CHANNEL_NAME

        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent for notification tap
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Pass data for handling notification opened
            putExtra("google.message_id", messageId)
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Get notification icon: FCM default_notification_icon meta-data -> app icon
        val notificationIcon = getNotificationIcon(context)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // Use message ID hash as notification ID for uniqueness
        val notificationId = messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notification displayed: $notificationId")
    }

    private fun getNotificationIcon(context: Context): Int {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val metaData = appInfo.metaData
            if (metaData != null) {
                val iconRes = metaData.getInt(FCM_ICON_META_KEY, 0)
                if (iconRes != 0) {
                    return iconRes
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get FCM default icon from meta-data", e)
        }
        // Fallback to app icon
        return context.applicationInfo.icon
    }
}
