package com.entrig.demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.entrig.sdk.Entrig
import com.entrig.sdk.models.EntrigConfig
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var emptyState: View
    private lateinit var adapter: RoomsAdapter
    private var userName: String? = null
    private val joinedGroupIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Supabase.initialize(applicationContext)
        Entrig.initialize(
            this,
            EntrigConfig(
                apiKey = "sk-proj-6cf6aea8-9f49110a909ab0594a29ae2997e6cf3762969b8134d620c0f09b6d0ae6c968b0"
            )
        )

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        emptyState = findViewById(R.id.empty_state)

        recyclerView.layoutManager = LinearLayoutManager(this)
        // Set empty adapter to avoid "No adapter attached" warning
        recyclerView.adapter = RoomsAdapter(emptyList(), emptySet(), null)

        fab.setOnClickListener {
            showCreateGroupDialog()
        }

        loadUserAndGroups()

        // Setup notification listeners
        Entrig.setOnForegroundNotificationListener { notification ->
            Toast.makeText(this, "Notification received: ${notification.title}", Toast.LENGTH_SHORT).show()
        }

        Entrig.setOnNotificationOpenedListener { notification ->
            // Handle notification tap - navigate based on type
            notification.data?.get("group_id")?.toString()?.let { groupId ->
                val groupName = notification.data?.get("group_name")?.toString() ?: "Group"
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("group_id", groupId)
                intent.putExtra("group_name", groupName)
                startActivity(intent)
            }
        }
    }

    private fun loadUserAndGroups() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load user name
                val userId = Supabase.client.auth.currentSessionOrNull()?.user?.id
                if (userId != null) {
                    val userData = SupabaseTable.users
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingle<User>()
                    userName = userData.name

                    runOnUiThread {
                        supportActionBar?.title = "Hi, $userName!"
                    }
                }

                loadGroups()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading user and groups", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadGroups() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = Supabase.client.auth.currentSessionOrNull()?.user?.id
                android.util.Log.d("MainActivity", "Loading groups for user: $userId")

                // Load all groups
                val groups = SupabaseTable.groups
                    .select()
                    .decodeList<Group>()

                android.util.Log.d("MainActivity", "Loaded ${groups.size} groups: ${groups.map { it.name }}")

                // Load user's joined groups
                if (userId != null) {
                    val participantData = SupabaseTable.groupMembers
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<GroupMember>()

                    android.util.Log.d("MainActivity", "User joined ${participantData.size} groups")

                    joinedGroupIds.clear()
                    joinedGroupIds.addAll(participantData.map { it.group_id })
                }

                runOnUiThread {
                    adapter = RoomsAdapter(groups, joinedGroupIds, userId)
                    adapter.onGroupClickListener = { group, isJoined ->
                        handleGroupClick(group, isJoined)
                    }
                    recyclerView.adapter = adapter

                    // Show/hide empty state
                    if (groups.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading groups", e)
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error loading groups: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCreateGroupDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create Group")

        val input = EditText(this)
        input.hint = "Group Name"
        builder.setView(input)

        builder.setPositiveButton("Create") { _, _ ->
            val groupName = input.text.toString()
            if (groupName.isNotEmpty()) {
                createGroup(groupName)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun handleGroupClick(group: Group, isJoined: Boolean) {
        if (isJoined) {
            // Already joined, navigate to chat
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("group_id", group.id)
            intent.putExtra("group_name", group.name)
            startActivity(intent)
        } else {
            // Show join confirmation dialog
            AlertDialog.Builder(this)
                .setTitle("Join Group")
                .setMessage("Do you want to join \"${group.name}\"?")
                .setPositiveButton("Join") { _, _ ->
                    joinGroup(group)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun joinGroup(group: Group) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = Supabase.client.auth.currentSessionOrNull()?.user?.id
                if (userId != null) {
                    // Add user to group members
                    SupabaseTable.groupMembers.insert(
                        GroupMember(group_id = group.id!!, user_id = userId)
                    )

                    joinedGroupIds.add(group.id)

                    runOnUiThread {
                        // Navigate to chat
                        val intent = Intent(this@MainActivity, ChatActivity::class.java)
                        intent.putExtra("group_id", group.id)
                        intent.putExtra("group_name", group.name)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error joining group", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error joining group: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createGroup(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = Supabase.client.auth.currentSessionOrNull()
                val userId = session?.user?.id
                if (userId != null) {
                    // Insert group and get the created group with ID
                    val createdGroup = SupabaseTable.groups
                        .insert(Group(name = name, created_by = userId)) {
                            select()
                        }
                        .decodeSingle<Group>()

                    // Add creator as participant
                    SupabaseTable.groupMembers.insert(
                        GroupMember(group_id = createdGroup.id!!, user_id = userId)
                    )

                    loadGroups()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error creating group", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error creating group: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // Unregister from Entrig before sign out
                    android.util.Log.d("MainActivity", "Unregistering from Entrig")
                    Entrig.unregister { success, error ->
                        if (success) {
                            android.util.Log.d("MainActivity", "Successfully unregistered from Entrig")
                        } else {
                            android.util.Log.e("MainActivity", "Failed to unregister from Entrig: $error")
                        }
                    }

                    Supabase.client.auth.signOut()
                    runOnUiThread {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
