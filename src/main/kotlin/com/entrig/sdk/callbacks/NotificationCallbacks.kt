package com.entrig.sdk.callbacks

import com.entrig.sdk.models.NotificationEvent

/**
 * Callback interface for handling notification events when app is in foreground.
 */
fun interface OnNotificationReceivedListener {
    /**
     * Called when a notification is received while the app is in foreground.
     *
     * @param notification The notification event data
     */
    fun onNotificationReceived(notification: NotificationEvent)
}

/**
 * Callback interface for handling notification clicks.
 */
fun interface OnNotificationClickListener {
    /**
     * Called when user clicks on a notification.
     *
     * @param notification The notification event data
     */
    fun onNotificationClick(notification: NotificationEvent)
}

/**
 * Callback interface for SDK initialization result.
 */
fun interface OnInitializationListener {
    /**
     * Called when SDK initialization completes.
     *
     * @param success True if initialization was successful
     * @param error Optional error message if initialization failed
     */
    fun onInitialized(success: Boolean, error: String?)
}

/**
 * Callback interface for registration result.
 */
fun interface OnRegistrationListener {
    /**
     * Called when user registration completes.
     *
     * @param success True if registration was successful
     * @param error Optional error message if registration failed
     */
    fun onRegistered(success: Boolean, error: String?)
}
