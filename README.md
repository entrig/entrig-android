# Entrig

**Push Notifications for Supabase**

Send push notifications to your Android app, triggered by database events.

## Prerequisites

1. **Create Entrig Account** - Sign up at [entrig.com](https://entrig.com?ref=pub.dev)

2. **Connect Supabase** - Authorize Entrig to access your Supabase project


3. **Upload FCM Service Account** - Upload Service Account JSON and provide your Application ID

---

## Installation

**Minimum Requirements:**
- Android 8.0 (API 26) or higher

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.entrig:entrig:0.0.9-dev'
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
Entrig.register("user-123") { success, error ->
    if (success) {
        // User registered for notifications
    }
}
```

#### Permission Callback (required for Android 13+)

On Android 13+, the SDK requests `POST_NOTIFICATIONS` permission automatically during `register()`. You must forward the permission result from your Activity:

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

> **Why is this needed?** Android delivers permission results to the Activity's `onRequestPermissionsResult`, not to libraries. There is no way for the SDK to intercept this without the Activity forwarding it. We evaluated using `ActivityResultRegistry` to avoid this, but it introduces lifecycle complexity that is unreliable in a singleton SDK.

<details>
<summary>Manual permission handling (click to expand)</summary>

To handle permissions yourself, disable automatic handling:

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
        Entrig.register("user-123")
    }
}
```

The `onRequestPermissionsResult` forwarding is still required.

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
