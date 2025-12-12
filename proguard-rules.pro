# Entrig SDK ProGuard Rules

# Keep public API
-keep public class com.entrig.sdk.Entrig {
    public *;
}

-keep public class com.entrig.sdk.models.** {
    public *;
}

-keep public interface com.entrig.sdk.callbacks.** {
    *;
}

# Keep Firebase Messaging Service
-keep class com.entrig.sdk.internal.EntrigMessagingService {
    *;
}

# Keep data classes
-keepclassmembers class com.entrig.sdk.models.** {
    *;
}

# Firebase rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
