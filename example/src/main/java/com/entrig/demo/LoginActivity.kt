package com.entrig.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Supabase.initialize(applicationContext)

        // Initialize Entrig SDK
        com.entrig.sdk.Entrig.initialize(
            this,
            com.entrig.sdk.models.EntrigConfig(
                apiKey = "sk-proj-6cf6aea8-9f49110a909ab0594a29ae2997e6cf3762969b8134d620c0f09b6d0ae6c968b0"
            )
        ) { success, error ->
            if (success) {
                android.util.Log.d("LoginActivity", "Entrig initialized successfully")
            } else {
                android.util.Log.e("LoginActivity", "Entrig initialization failed: $error")
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            Supabase.client.auth.awaitInitialization()
            checkExistingSession()
        }

        val nameEditText = findViewById<EditText>(R.id.name_edit_text)
        val loginButton = findViewById<Button>(R.id.login_button)

        loginButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    android.util.Log.d("LoginActivity", "=== SIGN IN PROCESS STARTED ===")
                    android.util.Log.d("LoginActivity", "Starting anonymous sign in")
                    Supabase.client.auth.signInAnonymously()
                    android.util.Log.d("LoginActivity", "Sign in successful")

                    val session = Supabase.client.auth.currentSessionOrNull()
                    android.util.Log.d("LoginActivity", "Session obtained: ${session != null}")

                    if (session != null) {
                        val userId = session.user?.id ?: ""
                        android.util.Log.d("LoginActivity", "User ID extracted: $userId")

                        val userData = mapOf(
                            "id" to userId,
                            "name" to name
                        )
                        android.util.Log.d("LoginActivity", "Upserting user to database: $userData")

                        SupabaseTable.users.upsert(userData)
                        android.util.Log.d("LoginActivity", "User upserted successfully to database")

                        android.util.Log.d("LoginActivity", "Switching to UI thread for Entrig registration")
                        runOnUiThread {
                            android.util.Log.d("LoginActivity", "=== ON UI THREAD - ABOUT TO CALL ENTRIG.REGISTER ===")
                            android.util.Log.d("LoginActivity", "Calling Entrig.register() with userId: $userId")

                            com.entrig.sdk.Entrig.register(this@LoginActivity, userId) { success, error ->
                                android.util.Log.d("LoginActivity", "=== ENTRIG.REGISTER CALLBACK INVOKED ===")
                                android.util.Log.d("LoginActivity", "Success: $success, Error: $error")
                                if (success) {
                                    android.util.Log.d("LoginActivity", "✓ Successfully registered with Entrig")
                                } else {
                                    android.util.Log.e("LoginActivity", "✗ Failed to register with Entrig: $error")
                                }

                                // Navigate to MainActivity after registration completes
                                android.util.Log.d("LoginActivity", "Starting MainActivity")
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                                android.util.Log.d("LoginActivity", "=== SIGN IN PROCESS COMPLETED ===")
                            }
                            android.util.Log.d("LoginActivity", "Entrig.register() method call completed (waiting for callback)")
                        }
                    } else {
                        android.util.Log.e("LoginActivity", "Session is null after sign in")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LoginActivity", "=== LOGIN FAILED ===", e)
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        android.util.Log.d("LoginActivity", "=== PERMISSION RESULT RECEIVED ===")
        android.util.Log.d("LoginActivity", "requestCode: $requestCode, permissions: ${permissions.joinToString()}, grantResults: ${grantResults.joinToString()}")

        // Forward to Entrig SDK
        com.entrig.sdk.Entrig.onRequestPermissionsResult(requestCode, grantResults)
    }

    private suspend fun checkExistingSession() {
        try {
            android.util.Log.d("LoginActivity", "=== CHECKING EXISTING SESSION ===")
            val session = Supabase.client.auth.currentSessionOrNull()
            if (session != null) {
                val userId = session.user?.id ?: ""
                android.util.Log.d("LoginActivity", "Existing session found for userId: $userId")
                runOnUiThread {
                    android.util.Log.d("LoginActivity", "=== ON UI THREAD - ABOUT TO CALL ENTRIG.REGISTER (EXISTING SESSION) ===")
                    android.util.Log.d("LoginActivity", "Calling Entrig.register() with userId: $userId")

                    com.entrig.sdk.Entrig.register(this@LoginActivity, userId) { success, error ->
                        android.util.Log.d("LoginActivity", "=== ENTRIG.REGISTER CALLBACK INVOKED (EXISTING SESSION) ===")
                        android.util.Log.d("LoginActivity", "Success: $success, Error: $error")
                        if (success) {
                            android.util.Log.d("LoginActivity", "✓ Successfully registered with Entrig (existing session)")
                        } else {
                            android.util.Log.e("LoginActivity", "✗ Failed to register with Entrig (existing session): $error")
                        }

                        // Navigate to MainActivity after registration completes
                        android.util.Log.d("LoginActivity", "Starting MainActivity (existing session)")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                        android.util.Log.d("LoginActivity", "=== EXISTING SESSION FLOW COMPLETED ===")
                    }
                    android.util.Log.d("LoginActivity", "Entrig.register() method call completed (waiting for callback)")
                }
            } else {
                android.util.Log.d("LoginActivity", "No existing session found")
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "=== ERROR CHECKING SESSION ===", e)
        }
    }
}