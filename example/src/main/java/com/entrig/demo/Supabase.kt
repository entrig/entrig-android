package com.entrig.demo

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object Supabase {
    private var _client: SupabaseClient? = null

    fun initialize(context: Context) {
        if (_client == null) {
            _client = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                install(Auth) {
                    flowType = FlowType.PKCE
                    scheme = "app"
                    host = "supabase.com"
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                }
                install(Postgrest)

                defaultSerializer = KotlinXSerializer(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("Supabase client not initialized. Call Supabase.initialize(context) first.")
}

object SupabaseTable {
    val users get() = Supabase.client.from("users")
    val messages get() = Supabase.client.from("messages")
    val groups get() = Supabase.client.from("groups")
    val groupMembers get() = Supabase.client.from("group_members")
}

// Data models
@kotlinx.serialization.Serializable
data class User(
    val id: String,
    val name: String
)

@kotlinx.serialization.Serializable
data class Message(
    val id: String? = null,
    val content: String,
    val user_id: String,
    val group_id: String
)

@kotlinx.serialization.Serializable
data class Group(
    val id: String? = null,
    val name: String,
    val created_by: String
)

@kotlinx.serialization.Serializable
data class GroupMember(
    val group_id: String,
    val user_id: String
)
