package com.entrig.sdk

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import com.entrig.sdk.callbacks.OnInitializationListener
import com.entrig.sdk.callbacks.OnNotificationOpenedListener
import com.entrig.sdk.callbacks.OnForegroundNotificationListener
import com.entrig.sdk.callbacks.OnRegistrationListener
import com.entrig.sdk.internal.FCMManager
import com.entrig.sdk.internal.KEY_USER_ID
import com.entrig.sdk.internal.NotificationHelper
import com.entrig.sdk.internal.PREFS_NAME
import com.entrig.sdk.internal.jsonDecode
import com.entrig.sdk.models.EntrigConfig
import com.entrig.sdk.models.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main SDK class for Entrig push notification service.
 *
 * Usage:
 * ```kotlin
 * // Initialize in Application class or Activity
 * val config = EntrigConfig(apiKey = "your-api-key")
 * Entrig.initialize(context, config) { success, error ->
 *     if (success) {
 *         // SDK initialized successfully
 *     }
 * }
 *
 * // Register a user
 * Entrig.register("user-123") { success, error ->
 *     if (success) {
 *         // User registered successfully
 *     }
 * }
 *
 * // Listen for notifications
 * Entrig.setOnForegroundNotificationListener { notification ->
 *     // Handle foreground notification
 * }
 *
 * Entrig.setOnNotificationOpenedListener { notification ->
 *     // Handle notification opened/clicked
 * }
 * ```
 */
object Entrig {

    private const val TAG = "EntrigSDK"
    private const val PERMISSION_REQUEST_CODE = 1001

    private val fcmManager = FCMManager()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Application context stored during initialization (safe for singleton - same lifecycle)
    private var applicationContext: Context? = null
    internal var config: EntrigConfig? = null
        private set
    private var onForegroundNotificationListener: OnForegroundNotificationListener? = null
    private var onNotificationOpenedListener: OnNotificationOpenedListener? = null
    private var cachedInitialNotification: NotificationEvent? = null
    private var initialNotificationConsumed = false

    // Permission handling state (no Context stored here)
    private var pendingUserId: String? = null
    private var pendingSdk: String? = null
    private var pendingRegistrationCallback: OnRegistrationListener? = null

    // Track processed intents to avoid duplicate handling
    private val processedIntents = mutableSetOf<String>()

    // Activity lifecycle tracking with WeakReference to prevent memory leaks
    // Suppressed: WeakReference allows GC, and lifecycle callbacks clear the reference
    @android.annotation.SuppressLint("StaticFieldLeak")
    private var currentActivity: WeakReference<Activity>? = null

    /**
     * Returns true if the app is currently in the foreground (has a resumed activity).
     */
    internal val isAppInForeground: Boolean
        get() = currentActivity?.get() != null
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            // Automatically handle notification clicks when activity is created
            processIntentOnce(activity.intent)
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            currentActivity = WeakReference(activity)
            // Automatically handle notification clicks when activity resumes (handles onNewIntent)
            processIntentOnce(activity.intent)
        }

        override fun onActivityPaused(activity: Activity) {
            if (currentActivity?.get() == activity) {
                currentActivity = null
            }
        }

        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivity?.get() == activity) {
                currentActivity = null
            }
        }
    }

    /**
     * Initializes the Entrig SDK with the provided configuration.
     * Safe to call multiple times - subsequent calls are ignored if already initialized.
     *
     * @param context Application or Activity context
     * @param config SDK configuration including API key
     * @param listener Optional callback for initialization result
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        config: EntrigConfig,
        listener: OnInitializationListener? = null
    ) {
        // Check if already initialized
        if (this.applicationContext != null) {
            listener?.onInitialized(true, null)
            return
        }

        // Store application context for SDK operations
        val appContext = context.applicationContext
        this.applicationContext = appContext
        this.config = config

        // Create notification channel proactively (before any notification arrives)
        NotificationHelper.createNotificationChannel(
            appContext,
            config.notificationChannelId,
            config.notificationChannelName
        )

        // Register activity lifecycle callbacks for automatic activity tracking
        if (appContext is Application) {
            appContext.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        }

        scope.launch(Dispatchers.IO) {
            val result = fcmManager.initialize(this@Entrig.applicationContext!!, config.apiKey)

            launch(Dispatchers.Main) {
                if (result.isSuccess) {
                    Log.d(TAG, "SDK initialized")
                    listener?.onInitialized(true, null)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "SDK initialization failed: $error")
                    listener?.onInitialized(false, error)
                }
            }
        }
    }


    /**
     * Registers a user for push notifications.
     *
     * On Android 13+, this will automatically request notification permission if
     * handlePermissionAutomatically is enabled in config.
     *
     * @param userId Unique identifier for the user
     * @param activity Optional activity for permission requests (recommended for reliable permission handling)
     * @param listener Optional callback for registration result
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        userId: String,
        activity: Activity? = null,
        sdk: String = "android",
        listener: OnRegistrationListener? = null
    ) {
        val cfg = config
        val appContext = applicationContext

        if (cfg == null || appContext == null) {
            Log.e(TAG, "Registration failed: SDK not initialized")
            listener?.onRegistered(false, "SDK not initialized. Call initialize() first.")
            return
        }

        // Check if already registered with the same userId (idempotency check)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUserId = prefs.getString(KEY_USER_ID, null)

        if (savedUserId == userId) {
            listener?.onRegistered(true, null)
            return
        }

        // Check if we need to request permission (Android 13+)
        if (cfg.handlePermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                // Store pending registration
                pendingUserId = userId
                pendingSdk = sdk
                pendingRegistrationCallback = listener

                // Get activity for permission request (prefer parameter, fallback to tracked activity)
                val activityForPermission = activity ?: currentActivity?.get()
                if (activityForPermission != null) {
                    ActivityCompat.requestPermissions(
                        activityForPermission,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    Log.e(TAG, "Registration failed: Activity required for permission on Android 13+")
                    listener?.onRegistered(
                        false,
                        "Activity required for permission request on Android 13+. Pass Activity to register() or call from an active Activity."
                    )
                    // Clear pending state on error
                    pendingUserId = null
                    pendingSdk = null
                    pendingRegistrationCallback = null
                }
                return
            }
        }

        // Permission already granted or not required, proceed with registration
        performRegistration(appContext, userId, sdk, listener)
    }

    /**
     * Manually request notification permission (Android 13+).
     * Returns true immediately on Android versions below 13.
     *
     * @param activity Activity context required for permission request
     * @param callback Callback with permission result
     */
    @JvmStatic
    fun requestPermission(activity: Activity, callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                callback(true)
                return
            }

            // Note: For manual permission request, integrate with Activity's onRequestPermissionsResult
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission not required for Android versions below 13
            callback(true)
        }
    }

    /**
     * Call this method from your Activity's onRequestPermissionsResult.
     *
     * @param requestCode The request code passed to requestPermissions
     * @param grantResults The grant results for the corresponding permissions
     */
    @JvmStatic
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Handle pending registration if exists
            val userId = pendingUserId
            val sdk = pendingSdk ?: "android"
            val callback = pendingRegistrationCallback
            val appContext = applicationContext

            if (userId != null && appContext != null) {
                // Always register token regardless of permission result
                // User can grant permission later and notifications will work
                performRegistration(appContext, userId, sdk, callback)

                // Clear pending state
                pendingUserId = null
                pendingSdk = null
                pendingRegistrationCallback = null
            }
        }
    }

    /**
     * Unregisters the current user from push notifications.
     *
     * @param listener Optional callback for unregistration result
     */
    @JvmStatic
    @JvmOverloads
    fun unregister(listener: ((Boolean, String?) -> Unit)? = null) {
        val appContext = applicationContext
        if (appContext == null) {
            listener?.invoke(false, "SDK not initialized. Call initialize() first.")
            return
        }

        scope.launch(Dispatchers.IO) {
            val result = fcmManager.unregister(appContext)

            launch(Dispatchers.Main) {
                if (result.isSuccess) {
                    Log.d(TAG, "User unregistered")
                    listener?.invoke(true, null)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Unregistration failed: $error")
                    listener?.invoke(false, error)
                }
            }
        }
    }

    /**
     * Sets a listener for notifications received while app is in foreground.
     *
     * @param listener Callback to handle foreground notifications
     */
    @JvmStatic
    fun setOnForegroundNotificationListener(listener: OnForegroundNotificationListener?) {
        onForegroundNotificationListener = listener
    }

    /**
     * Sets a listener for notification opened events.
     *
     * @param listener Callback to handle notification opened from any state
     */
    @JvmStatic
    fun setOnNotificationOpenedListener(listener: OnNotificationOpenedListener?) {
        onNotificationOpenedListener = listener
    }

    /**
     * Gets the initial notification if app was launched from a notification.
     * Returns null if already consumed or no initial notification.
     *
     * @return The initial notification event or null
     */
    @JvmStatic
    fun getInitialNotification(): NotificationEvent? {
        if (!initialNotificationConsumed && cachedInitialNotification != null) {
            initialNotificationConsumed = true
            val notification = cachedInitialNotification
            cachedInitialNotification = null
            return notification
        }
        return null
    }

    /**
     * Processes an intent once to avoid duplicate notification handling.
     * This is called automatically by the SDK via ActivityLifecycleCallbacks.
     * You can also call it manually from onCreate/onNewIntent if needed.
     *
     * @param intent The intent to process
     */
    @JvmStatic
    fun handleIntent(intent: Intent?) {
        processIntentOnce(intent)
    }

    // Internal methods

    private fun processIntentOnce(intent: Intent?) {
        val notificationData = extractNotificationData(intent) ?: return

        // Use message ID as unique identifier to prevent duplicate processing
        val messageId = intent?.extras?.getString("google.message_id") ?: return

        // Check if already processed
        if (processedIntents.contains(messageId)) {
            return
        }

        // Mark as processed
        processedIntents.add(messageId)

        // Limit set size to prevent memory leak
        if (processedIntents.size > 100) {
            val iterator = processedIntents.iterator()
            repeat(50) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }

        if (!initialNotificationConsumed) {
            // Cache as initial notification if not yet consumed
            cachedInitialNotification = notificationData
        }

        // Notify opened listener
        onNotificationOpenedListener?.onNotificationOpened(notificationData)
    }

    internal fun notifyNotificationReceived(notification: NotificationEvent) {
        scope.launch(Dispatchers.Main) {
            onForegroundNotificationListener?.onForegroundNotification(notification)
        }
    }

    internal suspend fun refreshToken(context: Context, userId: String, token: String) {
        fcmManager.registerWithToken(context, userId, token)
    }

    /**
     * Reports delivery status to server.
     * Called internally when notification is delivered or read.
     */
    internal suspend fun reportDeliveryStatus(deliveryId: String, status: String) {
        fcmManager.reportDeliveryStatus(deliveryId, status)
    }

    private fun performRegistration(
        context: Context,
        userId: String,
        sdk: String = "android",
        listener: OnRegistrationListener?
    ) {
        scope.launch(Dispatchers.IO) {
            val result = fcmManager.register(context, userId, sdk)

            launch(Dispatchers.Main) {
                if (result.isSuccess) {
                    Log.d(TAG, "User registered: $userId")
                    listener?.onRegistered(true, null)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Registration failed: $error")
                    listener?.onRegistered(false, error)
                }
            }
        }
    }

    private fun extractNotificationData(intent: Intent?): NotificationEvent? {
        val extras = intent?.extras ?: return null

        // Check if this is a FCM notification by looking for message ID
        val messageId = extras.getString("google.message_id") ?: extras.getString("message_id")
        if (messageId == null) {
            return null
        }

        // Check for FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return null
        }

        // Extract and decode the payload JSON string
        val payloadString = extras.getString("payload")
        val payload = payloadString?.let { jsonDecode(it) }?.toMutableMap() ?: mutableMapOf()

        // Extract title, body, type, and delivery_id from data
        val title = payload.remove("title")?.toString() ?: ""
        val body = payload.remove("body")?.toString() ?: ""
        val type = payload.remove("type")?.toString()
        val deliveryId = payload.remove("delivery_id")?.toString()

        // Report "read" status when notification is opened
        if (deliveryId != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    reportDeliveryStatus(deliveryId, "read")
                    Log.d(TAG, "Read status reported: $deliveryId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to report read status: $deliveryId", e)
                }
            }
        }

        return NotificationEvent(
            title = title,
            body = body,
            type = type,
            deliveryId = deliveryId,
            data = payload
        )
    }
}
