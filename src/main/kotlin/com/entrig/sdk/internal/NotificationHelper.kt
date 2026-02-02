package com.entrig.sdk.internal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.NotificationEvent

internal object NotificationHelper {

    internal const val DEFAULT_CHANNEL_ID = "entrig_default"
    internal const val DEFAULT_CHANNEL_NAME = "General"
    private const val FCM_ICON_META_KEY = "com.google.firebase.messaging.default_notification_icon"

    /**
     * Creates the notification channel proactively.
     * Should be called during SDK initialization to ensure channel exists before any notification arrives.
     * This method is idempotent - it won't create a duplicate channel if one with the same ID already exists.
     */
    fun createNotificationChannel(context: Context, channelId: String? = null, channelName: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = channelId ?: DEFAULT_CHANNEL_ID
            val name = channelName ?: DEFAULT_CHANNEL_NAME

            // Check if channel already exists to avoid duplicate creation
            if (notificationManager.getNotificationChannel(id) != null) {
                return
            }

            val channel = NotificationChannel(
                id,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

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

        // Ensure notification channel exists (idempotent - won't duplicate if already created during initialization)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channelName = config?.notificationChannelName ?: DEFAULT_CHANNEL_NAME
                createNotificationChannel(context, channelId, channelName)
            }
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
    }

    private fun getNotificationIcon(context: Context): Int {
        // 1. Try FCM default notification icon from meta-data
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
        } catch (_: Exception) {
            // Fallback to app icon
        }

        // 2. Try app icon
        val appIcon = context.applicationInfo.icon
        if (appIcon != 0) {
            return appIcon
        }

        // 3. Fallback to Android's default notification icon
        return android.R.drawable.ic_dialog_info
    }
}
