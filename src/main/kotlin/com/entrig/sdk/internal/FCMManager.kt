package com.entrig.sdk.internal

import android.content.Context
import android.util.Log
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

            Log.d(TAG, "Firebase initialization successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
            Result.failure(e)
        }
    }

    suspend fun register(context: Context, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val app = firebaseApp ?: return@withContext Result.failure(
                IllegalStateException("Firebase not initialized. Call initialize() first.")
            )

            val fcmInstance = app.get(FirebaseMessaging::class.java)
            val token = Tasks.await(fcmInstance.token)

            registerWithToken(context, userId, token)
        } catch (e: Exception) {
            Log.e(TAG, "Token registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun registerWithToken(context: Context, userId: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val key = apiKey ?: return@withContext Result.failure(
                    IllegalStateException("API key not set. Call initialize() first.")
                )

                // Check if already registered with same userId and token
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedUserId = prefs.getString(KEY_USER_ID, null)
                val savedToken = prefs.getString(KEY_FCM_TOKEN, null)

                if (savedUserId == userId && savedToken == token) {
                    Log.d(TAG, "Already registered with same userId and token, skipping registration")
                    return@withContext Result.success(Unit)
                }

                val response = sendPostRequest(
                    url = "$BASE_URL/register",
                    headers = mapOf("Authorization" to "Bearer $key"),
                    body = mapOf(
                        "user_id" to userId,
                        "fcm_token" to token,
                        "sdk" to "android"
                    )
                )

                Log.d(TAG, "Token registration response: $response")

                // Save registration ID, user ID, and token to SharedPreferences
                val responseData = jsonDecode(response)
                val registrationId = responseData["id"]?.toString()

                if (registrationId != null) {
                    prefs.edit()
                        .putString(KEY_REGISTRATION_ID, registrationId)
                        .putString(KEY_USER_ID, userId)
                        .putString(KEY_FCM_TOKEN, token)
                        .apply()
                    Log.d(TAG, "Saved registration ID: $registrationId")
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

            val response = sendPostRequest(
                url = "$BASE_URL/unregister",
                headers = mapOf("Authorization" to "Bearer $key"),
                body = mapOf("id" to registrationId)
            )

            Log.d(TAG, "FCM Unregister response: $response")

            // Clear the registration ID, user ID, and token after successful un-registration
            prefs.edit()
                .remove(KEY_REGISTRATION_ID)
                .remove(KEY_USER_ID)
                .remove(KEY_FCM_TOKEN)
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "FCM Unregister failed", e)
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

            val responseText = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: throw e
            }

            responseText
        } finally {
            connection.disconnect()
        }
    }

    fun getFirebaseApp(): FirebaseApp? = firebaseApp
}
