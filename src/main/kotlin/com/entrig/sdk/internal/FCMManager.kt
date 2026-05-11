package com.entrig.sdk.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.entrig.sdk.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Internal class for managing Firebase Cloud Messaging operations.
 * Not intended for direct use by SDK consumers.
 */
internal class FCMManager {
    private var firebaseApp: FirebaseApp? = null
    private var apiKey: String? = null

    companion object {
        private const val TAG = "EntrigFCM"
        private const val BASE_URL = "https://wlbsugnskuojugsubnjj.supabase.co/functions/v1"
        private const val FIREBASE_APP_NAME = "entrig-custom-app"
        private const val DEFAULT_SDK_VERSION = BuildConfig.SDK_VERSION
    }

    suspend fun initialize(context: Context, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            this@FCMManager.apiKey = apiKey

            val response = sendPostRequest(
                url = "$BASE_URL/fcm-params",
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                body = mapOf("appId" to context.packageName)
            )

            val responseData = jsonDecode(response)
            val data = responseData["data"] as? Map<*, *>
                ?: return@withContext Result.failure(Exception("Invalid response format"))

            val firebaseOptions = FirebaseOptions.Builder()
                .setGcmSenderId(data["senderId"]?.toString())
                .setApplicationId(data["appId"]?.toString() ?: "")
                .setApiKey(data["apiKey"]?.toString() ?: "")
                .setProjectId(data["projectId"]?.toString())
                .build()

            firebaseApp = try {
                FirebaseApp.getInstance(FIREBASE_APP_NAME)
            } catch (e: IllegalStateException) {
                FirebaseApp.initializeApp(context, firebaseOptions, FIREBASE_APP_NAME)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
            Result.failure(e)
        }
    }

    suspend fun register(
        context: Context,
        userId: String,
        sdk: String = "android",
        sdkVersion: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val app = firebaseApp ?: return@withContext Result.failure(
                IllegalStateException("Firebase not initialized. Call initialize() first.")
            )

            val fcmInstance = app.get(FirebaseMessaging::class.java)
            val token = Tasks.await(fcmInstance.token)

            registerWithToken(context, userId, token, sdk, sdkVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Token registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun registerWithToken(
        context: Context,
        userId: String,
        token: String,
        sdk: String? = null,
        sdkVersion: String? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val key = apiKey ?: return@withContext Result.failure(
                    IllegalStateException("API key not set. Call initialize() first.")
                )

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                // Resolve sdk: use parameter, fall back to saved value, default to "android"
                val resolvedSdk = sdk ?: prefs.getString(KEY_SDK, "android") ?: "android"
                val resolvedSdkVersion = sdkVersion
                    ?: prefs.getString(KEY_SDK_VERSION, DEFAULT_SDK_VERSION)
                    ?: DEFAULT_SDK_VERSION

                // Check if already registered with same userId, token, sdk, and sdkVersion
                val savedUserId = prefs.getString(KEY_USER_ID, null)
                val savedToken = prefs.getString(KEY_FCM_TOKEN, null)
                val savedSdk = prefs.getString(KEY_SDK, null)
                val savedSdkVersion = prefs.getString(KEY_SDK_VERSION, null)

                if (
                    savedUserId == userId &&
                    savedToken == token &&
                    savedSdk == resolvedSdk &&
                    savedSdkVersion == resolvedSdkVersion
                ) {
                    return@withContext Result.success(Unit)
                }

                val response = sendPostRequest(
                    url = "$BASE_URL/register",
                    headers = mapOf("Authorization" to "Bearer $key"),
                    body = mapOf(
                        "user_id" to userId,
                        "fcm_token" to token,
                        "sdk" to resolvedSdk,
                        "sdk_version" to resolvedSdkVersion,
                        "is_debug" to ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                    )
                )

                // Save registration ID, user ID, token, sdk, and sdkVersion to SharedPreferences
                val responseData = jsonDecode(response)
                val registrationId = responseData["id"]?.toString()

                if (registrationId != null) {
                    prefs.edit()
                        .putString(KEY_REGISTRATION_ID, registrationId)
                        .putString(KEY_USER_ID, userId)
                        .putString(KEY_FCM_TOKEN, token)
                        .putString(KEY_SDK, resolvedSdk)
                        .putString(KEY_SDK_VERSION, resolvedSdkVersion)
                        .apply()
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Token registration failed", e)
                Result.failure(e)
            }
        }

    suspend fun unregister(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey ?: return@withContext Result.failure(
                IllegalStateException("API key not set. Call initialize() first.")
            )

            // Retrieve registration ID from SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val registrationId = prefs.getString(KEY_REGISTRATION_ID, null)
                ?: return@withContext Result.failure(Exception("No registration ID found"))

            val app = firebaseApp ?: return@withContext Result.failure(
                IllegalStateException("Firebase not initialized")
            )

            val fcmInstance = app.get(FirebaseMessaging::class.java)
            Tasks.await(fcmInstance.deleteToken())

            sendPostRequest(
                url = "$BASE_URL/unregister",
                headers = mapOf("Authorization" to "Bearer $key"),
                body = mapOf("id" to registrationId)
            )

            // Clear the registration ID, user ID, token, sdk, and sdkVersion after successful un-registration
            prefs.edit()
                .remove(KEY_REGISTRATION_ID)
                .remove(KEY_USER_ID)
                .remove(KEY_FCM_TOKEN)
                .remove(KEY_SDK)
                .remove(KEY_SDK_VERSION)
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister failed", e)
            Result.failure(e)
        }
    }

    private suspend fun sendPostRequest(
        url: String,
        headers: Map<String, String>,
        body: Map<String, Any>?
    ): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000 // 30 seconds
                readTimeout = 30000 // 30 seconds
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            if (body != null) {
                val jsonBody = JSONObject(body).toString()
                BufferedWriter(OutputStreamWriter(connection.outputStream)).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            } else {
                BufferedWriter(OutputStreamWriter(connection.outputStream)).use { writer ->
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val message = when (responseCode) {
                    401 -> "Invalid API key. Check your EntrigConfig apiKey."
                    403 -> "Access denied. Verify your API key has the required permissions."
                    404 -> "Entrig endpoint not found. Ensure you're using a compatible SDK version."
                    422 -> "Invalid request data: $errorBody"
                    429 -> "Rate limit exceeded. Please retry after a short delay."
                    in 500..599 -> "Entrig server error ($responseCode). Please try again later."
                    else -> "Request failed ($responseCode): $errorBody"
                }
                throw Exception(message)
            }
        } finally {
            connection.disconnect()
        }
    }

    fun getFirebaseApp(): FirebaseApp? = firebaseApp

    /**
     * Reports delivery status (delivered/read) to the server.
     * Called internally by the SDK when notifications are received or opened.
     */
    suspend fun reportDeliveryStatus(
        deliveryId: String,
        status: String  // "delivered" or "read"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey ?: return@withContext Result.failure(
                IllegalStateException("API key not set. Call initialize() first.")
            )

            sendPostRequest(
                url = "$BASE_URL/delivery-status",
                headers = mapOf("Authorization" to "Bearer $key"),
                body = mapOf(
                    "delivery_id" to deliveryId,
                    "status" to status,
                    "timestamp" to java.time.Instant.now().toString()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report delivery status: $deliveryId", e)
            Result.failure(e)
        }
    }
}
