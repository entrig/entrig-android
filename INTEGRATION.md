# Entrig Android SDK - Integration Guide

## Quick Start

### Step 1: Add the SDK to your project

**Option A: Local Module**

1. Copy the `entrig-android` folder to your project directory
2. Add to `settings.gradle`:
```gradle
include ':entrig-android'
```

3. Add dependency in your app's `build.gradle`:
```gradle
dependencies {
    implementation project(':entrig-android')
}
```

**Option B: Add to local Maven (for multiple projects)**

```bash
cd entrig-android
./gradlew publishToMavenLocal
```

Then in your app's `build.gradle`:
```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation 'com.entrig:entrig-android:1.0.0'
}
```

### Step 2: Update AndroidManifest.xml

The SDK's manifest will be automatically merged. No additional configuration needed.

### Step 3: Initialize in Application class

Create or update your Application class:

```kotlin
// Application.kt
import android.app.Application
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.EntrigConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Entrig
        val config = EntrigConfig(
            apiKey = BuildConfig.ENTRIG_API_KEY, // Store in build.gradle or secrets
            handlePermissionAutomatically = true
        )

        Entrig.initialize(this, config) { success, error ->
            if (success) {
                android.util.Log.d("App", "Entrig initialized successfully")
            } else {
                android.util.Log.e("App", "Entrig init failed: $error")
            }
        }
    }
}
```

Register your Application class in `AndroidManifest.xml`:
```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```

### Step 4: Store API Key Securely

**Option A: Using buildConfigField**

In your app's `build.gradle`:
```gradle
android {
    defaultConfig {
        buildConfigField "String", "ENTRIG_API_KEY", "\"${project.findProperty('ENTRIG_API_KEY') ?: ''}\""
    }
}
```

In `gradle.properties` (add to `.gitignore`):
```properties
ENTRIG_API_KEY=your_api_key_here
```

**Option B: Using local.properties**

```kotlin
// In Application.kt
private fun getApiKey(): String {
    val properties = Properties()
    val localProperties = File(rootDir, "local.properties")
    if (localProperties.exists()) {
        properties.load(FileInputStream(localProperties))
    }
    return properties.getProperty("entrig.api.key", "")
}
```

### Step 5: Register Users

Register users after they log in:

```kotlin
// After successful login
val userId = currentUser.id // Your user ID
Entrig.register(this, userId) { success, error ->
    if (success) {
        // User registered for notifications
    } else {
        // Handle error
        Log.e("App", "Registration failed: $error")
    }
}
```

### Step 6: Handle Notifications in MainActivity

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNotificationHandlers()

        // Handle notification that launched the app
        Entrig.handleIntent(intent)
    }

    private fun setupNotificationHandlers() {
        // Foreground notifications
        Entrig.setOnNotificationReceivedListener { notification ->
            // Show in-app notification UI
            showInAppNotification(notification)
        }

        // Notification clicks
        Entrig.setOnNotificationClickListener { notification ->
            handleNotificationAction(notification)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle notification click when app is running
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

    private fun showInAppNotification(notification: NotificationEvent) {
        // Your custom UI logic
        Snackbar.make(
            findViewById(android.R.id.content),
            "${notification.title}: ${notification.body}",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun handleNotificationAction(notification: NotificationEvent) {
        when (notification.type) {
            "message" -> {
                val chatId = notification.data?.get("chat_id") as? String
                // Navigate to chat screen
            }
            "order" -> {
                val orderId = notification.data?.get("order_id") as? String
                // Navigate to order screen
            }
            else -> {
                // Default action
            }
        }
    }
}
```

## Advanced Usage

### Custom Permission Handling

If you want to handle permissions yourself:

```kotlin
// In initialization
val config = EntrigConfig(
    apiKey = "your-api-key",
    handlePermissionAutomatically = false
)

// Later, when you want to request permission
Entrig.requestPermission(this) { granted ->
    if (granted) {
        // Now register the user
        Entrig.register(this, userId)
    } else {
        // Show explanation or handle gracefully
    }
}
```

### Checking Initial Notification

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val initialNotification = Entrig.getInitialNotification()
    if (initialNotification != null) {
        // App was launched from notification
        handleDeepLink(initialNotification)
    }
}
```

### Unregistering Users

```kotlin
// When user logs out
Entrig.unregister { success, error ->
    if (success) {
        Log.d("App", "User unregistered from notifications")
    }
}
```

## Testing

### Test Notification Reception

1. Initialize the SDK with your API key
2. Register a test user
3. Send a test notification from your Entrig dashboard
4. Verify the notification appears in:
   - System notification tray (background/killed)
   - Your foreground listener (foreground)

### Test Notification Clicks

1. Send a notification with custom data:
```json
{
  "type": "test",
  "custom_key": "custom_value"
}
```

2. Tap the notification
3. Verify `OnNotificationClickListener` is called
4. Check that `notification.data` contains your custom data

## Troubleshooting

### Notifications not received

1. Verify API key is correct
2. Check that user is registered: Look for "User registered successfully" log
3. Ensure Firebase Messaging Service is declared in manifest (auto-merged)
4. Check network connectivity
5. Verify Google Play Services is available on device

### Permission not requested

1. Ensure you're on Android 13+ (API 33+)
2. Verify `handlePermissionAutomatically = true`
3. Check that you're passing an Activity context to `register()`
4. Implement `onRequestPermissionsResult()` callback

### Notification clicks not working

1. Verify `Entrig.handleIntent(intent)` is called in `onCreate()` and `onNewIntent()`
2. Ensure notification click listener is set before handling intent
3. Check intent extras contain FCM data

### Build errors

1. Ensure minimum SDK is 21+
2. Verify Kotlin version compatibility (2.1.0+)
3. Check that Google Play Services is added
4. Clean and rebuild: `./gradlew clean build`

## Migration from Flutter Plugin

If you're migrating from the Flutter plugin:

1. The API is similar but uses callbacks instead of method channels
2. Replace Flutter method calls with SDK methods:
   - `methodChannel.invokeMethod('init')` → `Entrig.initialize()`
   - `methodChannel.invokeMethod('register')` → `Entrig.register()`
3. Replace event channels with listeners:
   - `EventChannel` → `setOnNotificationReceivedListener()`
4. Permission handling is now automatic (or manual if preferred)

## Best Practices

1. **Initialize early**: Initialize in Application class, not Activity
2. **Secure API key**: Never commit API keys to version control
3. **Handle errors**: Always check success/error in callbacks
4. **Register after login**: Only register after user authentication
5. **Unregister on logout**: Clear registration when user logs out
6. **Test thoroughly**: Test foreground, background, and killed states
7. **Custom data**: Use `type` field for routing, `data` for parameters

## ProGuard/R8

If you're using code obfuscation, the SDK includes ProGuard rules. No additional configuration needed.

## Support

- GitHub Issues: [Repository URL]
- Email: support@entrig.com
- Documentation: [Docs URL]
