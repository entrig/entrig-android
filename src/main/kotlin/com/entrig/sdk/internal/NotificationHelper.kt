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
    internal const val DEFAULT_CHANNEL_ID = "entrig_default"
    internal const val DEFAULT_CHANNEL_NAME = "General"
    private const val FCM_ICON_META_KEY = "com.google.firebase.messaging.default_notification_icon"

    /**
     * Creates the notification channel proactively.
     * Should be called during SDK initialization to ensure channel exists before any notification arrives.
     */
    fun createNotificationChannel(context: Context, channelId: String? = null, channelName: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = channelId ?: DEFAULT_CHANNEL_ID
            val name = channelName ?: DEFAULT_CHANNEL_NAME

            val channel = NotificationChannel(
                id,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $id ($name)")
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get FCM default icon from meta-data", e)
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
