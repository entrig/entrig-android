package com.entrig.demo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MessageWithUser(
    val id: String?,
    val content: String,
    val user_id: String,
    val group_id: String,
    val userName: String
)

class ChatActivity : AppCompatActivity() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var emptyState: View
    private lateinit var adapter: MessagesAdapter
    private var groupId: String? = null
    private var groupName: String? = null
    private val userNames = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        messagesRecyclerView = findViewById(R.id.messages_recycler_view)
        messageEditText = findViewById(R.id.message_edit_text)
        sendButton = findViewById(R.id.send_button)
        emptyState = findViewById(R.id.empty_state)

        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        groupId = intent.getStringExtra("group_id")
        groupName = intent.getStringExtra("group_name")

        supportActionBar?.title = groupName

        loadMessages()

        sendButton.setOnClickListener {
            val content = messageEditText.text.toString()
            if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUserId = Supabase.client.auth.currentSessionOrNull()?.user?.id

                // Load messages with user names using JOIN
                val data = SupabaseTable.messages
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, users!inner(name)")) {
                        filter {
                            eq("group_id", groupId ?: "")
                        }
                        order(column = "created_at", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    }
                    .decodeList<kotlinx.serialization.json.JsonObject>()

                // Parse messages and extract user names
                val messagesWithUsers = data.map { json ->
                    val userId = json["user_id"]?.jsonPrimitive?.content ?: ""
                    val userName = json["users"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown"

                    userNames[userId] = userName

                    MessageWithUser(
                        id = json["id"]?.jsonPrimitive?.content,
                        content = json["content"]?.jsonPrimitive?.content ?: "",
                        user_id = userId,
                        group_id = json["group_id"]?.jsonPrimitive?.content ?: "",
                        userName = userName
                    )
                }

                runOnUiThread {
                    if (messagesWithUsers.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        messagesRecyclerView.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        messagesRecyclerView.visibility = View.VISIBLE
                    }

                    adapter = MessagesAdapter(messagesWithUsers, currentUserId ?: "")
                    messagesRecyclerView.adapter = adapter
                    // Scroll to the last (latest) message
                    if (messagesWithUsers.isNotEmpty()) {
                        messagesRecyclerView.scrollToPosition(messagesWithUsers.size - 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error loading messages", e)
                e.printStackTrace()
            }
        }
    }

    private fun sendMessage(content: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = Supabase.client.auth.currentSessionOrNull()
                val userId = session?.user?.id
                if (userId != null) {
                    val message = Message(
                        content = content,
                        user_id = userId,
                        group_id = groupId!!
                    )
                    SupabaseTable.messages.insert(message)
                    runOnUiThread {
                        messageEditText.text.clear()
                    }
                    loadMessages()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}