# Entrig Android SDK

Native Android SDK for Entrig - No-code Push Notifications for Supabase.

## Installation

### Using Gradle (Local Module)

1. Add the SDK module to your project's `settings.gradle`:

```gradle
include ':entrig-android'
project(':entrig-android').projectDir = new File('../entrig-android')
```

2. Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation project(':entrig-android')
}
```

### Using Maven/JitPack (Future)

```gradle
dependencies {
    implementation 'com.entrig:entrig-android:1.0.0'
}
```

## Setup

### 1. Initialize SDK

Initialize the SDK in your Application class or MainActivity:

```kotlin
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.EntrigConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = EntrigConfig(
            apiKey = "your-entrig-api-key",
            handlePermissionAutomatically = true // Optional, defaults to true
        )

        Entrig.initialize(this, config) { success, error ->
            if (success) {
                Log.d("Entrig", "SDK initialized successfully")
            } else {
                Log.e("Entrig", "SDK initialization failed: $error")
            }
        }
    }
}
```

### 2. Register User

Register a user to receive push notifications:

```kotlin
// In your Activity
Entrig.register(this, "user-123") { success, error ->
    if (success) {
        Log.d("Entrig", "User registered successfully")
    } else {
        Log.e("Entrig", "Registration failed: $error")
    }
}
```

**Note:** On Android 13 (API 33) and above, this will automatically request notification permission if `handlePermissionAutomatically` is set to true.

### 3. Handle Permission Request (Android 13+)

If you're targeting Android 13+, add the permission callback to your Activity:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Entrig.onRequestPermissionsResult(requestCode, grantResults)
    }
}
```

Alternatively, manually request permission:

```kotlin
Entrig.requestPermission(this) { granted ->
    if (granted) {
        // Permission granted, now register user
        Entrig.register(this, "user-123")
    }
}
```

### 4. Listen for Notifications

#### Foreground Notifications

Handle notifications when your app is in the foreground:

```kotlin
Entrig.setOnNotificationReceivedListener { notification ->
    Log.d("Entrig", "Notification received: ${notification.title}")
    Log.d("Entrig", "Body: ${notification.body}")
    Log.d("Entrig", "Type: ${notification.type}")
    Log.d("Entrig", "Data: ${notification.data}")

    // Show custom UI, update app state, etc.
}
```

#### Notification Clicks

Handle notification clicks (background and terminated states):

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle notification click when app launches
        Entrig.handleIntent(intent)

        // Set click listener
        Entrig.setOnNotificationClickListener { notification ->
            Log.d("Entrig", "Notification clicked: ${notification.title}")

            // Navigate to specific screen based on notification type
            when (notification.type) {
                "chat" -> navigateToChatScreen(notification.data)
                "order" -> navigateToOrderScreen(notification.data)
                else -> {
                    // Handle default case
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle notification click when app is already running
        Entrig.handleIntent(intent)
    }
}
```

#### Get Initial Notification

Check if the app was launched from a notification:

```kotlin
val initialNotification = Entrig.getInitialNotification()
if (initialNotification != null) {
    Log.d("Entrig", "App launched from notification: ${initialNotification.title}")
    // Handle the notification that launched the app
}
```

### 5. Unregister User

Unregister a user from push notifications:

```kotlin
Entrig.unregister { success, error ->
    if (success) {
        Log.d("Entrig", "User unregistered successfully")
    } else {
        Log.e("Entrig", "Unregistration failed: $error")
    }
}
```

## API Reference

### Entrig

Main SDK class for managing push notifications.

#### Methods

- **`initialize(context: Context, config: EntrigConfig, listener: OnInitializationListener?)`**

  Initializes the SDK with your API key.

  - `context`: Application or Activity context
  - `config`: SDK configuration
  - `listener`: Optional callback for initialization result

- **`register(context: Context, userId: String, listener: OnRegistrationListener?)`**

  Registers a user for push notifications.

  - `context`: Activity context (required for permission on Android 13+)
  - `userId`: Unique identifier for the user
  - `listener`: Optional callback for registration result

- **`unregister(listener: ((Boolean, String?) -> Unit)?)`**

  Unregisters the current user from push notifications.

- **`requestPermission(activity: Activity, callback: (Boolean) -> Unit)`**

  Manually requests notification permission (Android 13+).

- **`onRequestPermissionsResult(requestCode: Int, grantResults: IntArray)`**

  Call from Activity's `onRequestPermissionsResult`.

- **`setOnNotificationReceivedListener(listener: OnNotificationReceivedListener?)`**

  Sets listener for foreground notifications.

- **`setOnNotificationClickListener(listener: OnNotificationClickListener?)`**

  Sets listener for notification clicks.

- **`getInitialNotification(): NotificationEvent?`**

  Gets the notification that launched the app (if any).

- **`handleIntent(intent: Intent?)`**

  Processes notification click intents. Call from `onCreate` and `onNewIntent`.

### EntrigConfig

Configuration model for SDK initialization.

```kotlin
data class EntrigConfig(
    val apiKey: String,
    val handlePermissionAutomatically: Boolean = true
)
```

### NotificationEvent

Model representing a notification event.

```kotlin
data class NotificationEvent(
    val title: String?,
    val body: String?,
    val type: String?,
    val data: Map<String, Any?>?
)
```

## Complete Example

```kotlin
// Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = EntrigConfig(apiKey = "your-api-key")
        Entrig.initialize(this, config) { success, error ->
            if (success) {
                Log.d("App", "Entrig initialized")
            }
        }
    }
}

// MainActivity
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup notification handlers
        setupEntrig()

        // Handle initial notification click
        Entrig.handleIntent(intent)

        // Register user after login
        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val userId = "user-123"
            Entrig.register(this, userId) { success, error ->
                if (success) {
                    Toast.makeText(this, "Registered for notifications", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupEntrig() {
        // Handle foreground notifications
        Entrig.setOnNotificationReceivedListener { notification ->
            showCustomNotificationUI(notification)
        }

        // Handle notification clicks
        Entrig.setOnNotificationClickListener { notification ->
            when (notification.type) {
                "chat" -> openChatScreen(notification.data?.get("chatId") as? String)
                "order" -> openOrderScreen(notification.data?.get("orderId") as? String)
            }
        }

        // Check if app was launched from notification
        Entrig.getInitialNotification()?.let { notification ->
            handleInitialNotification(notification)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Entrig.handleIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Entrig.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun showCustomNotificationUI(notification: NotificationEvent) {
        // Show custom in-app notification
    }

    private fun handleInitialNotification(notification: NotificationEvent) {
        // Navigate to appropriate screen
    }

    private fun openChatScreen(chatId: String?) {
        // Navigate to chat
    }

    private fun openOrderScreen(orderId: String?) {
        // Navigate to order details
    }
}
```

## Java Usage

The SDK also supports Java with proper annotations:

```java
// Initialize
EntrigConfig config = new EntrigConfig("your-api-key", true);
Entrig.initialize(this, config, (success, error) -> {
    if (success) {
        Log.d("Entrig", "Initialized");
    }
});

// Register
Entrig.register(this, "user-123", (success, error) -> {
    if (success) {
        Log.d("Entrig", "Registered");
    }
});

// Listen for notifications
Entrig.setOnNotificationReceivedListener(notification -> {
    Log.d("Entrig", "Received: " + notification.getTitle());
});

Entrig.setOnNotificationClickListener(notification -> {
    Log.d("Entrig", "Clicked: " + notification.getTitle());
});

// Handle intent
Entrig.handleIntent(getIntent());
```

## Requirements

- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 2.1.0+
- Google Play Services

## Permissions

The SDK requires the following permissions (automatically added via manifest merge):

- `android.permission.INTERNET` - Required for API communication
- `android.permission.POST_NOTIFICATIONS` - Required for notifications on Android 13+

## License

Copyright (c) 2024 Entrig

## Support

For issues, questions, or feature requests, please visit:
- GitHub: [Your Repository URL]
- Documentation: [Your Docs URL]
- Email: support@entrig.com
