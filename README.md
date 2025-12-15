# Entrig Android SDK

Native Android SDK for Entrig - No-code Push Notifications for Supabase.

## Installation

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.entrig:entrig-android:0.0.2-dev'
}
```

## Usage

### Initialize

```kotlin
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.EntrigConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Entrig.initialize(this, EntrigConfig(apiKey = "your-entrig-api-key"))
    }
}
```

### Register User

```kotlin
Entrig.register(this, "user-123")
```

<details>
<summary>Manual permission handling (click to expand)</summary>

By default, the SDK handles permissions automatically. To disable:

```kotlin
val config = EntrigConfig(
    apiKey = "your-entrig-api-key",
    handlePermission = false
)
Entrig.initialize(this, config)
```

Then request permission manually before registering:

```kotlin
Entrig.requestPermission(this) { granted ->
    if (granted) {
        Entrig.register(this, "user-123")
    }
}
```

Add this to your Activity:

```kotlin
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Entrig.onRequestPermissionsResult(requestCode, grantResults)
}
```

</details>

### Listen for Notifications

```kotlin
// Foreground notifications (when app is open)
Entrig.setOnForegroundNotificationListener { notification ->
    // Access: notification.title, notification.body, notification.type, notification.data
}

// Notification opened (when user taps notification)
Entrig.setOnNotificationOpenedListener { notification ->
    // Navigate based on notification.type or notification.data
}
```

### Unregister User

```kotlin
Entrig.unregister()
```


## Permissions

The SDK requires the following permissions (automatically added via manifest merge):

- `android.permission.INTERNET` - Required for API communication
- `android.permission.POST_NOTIFICATIONS` - Required for notifications on Android 13+


## Support

For issues, questions, or feature requests, please visit:
- Email: team@entrig.com
