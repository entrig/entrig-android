package com.entrig.demo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoomsAdapter(
    private val groups: List<Group>,
    private val joinedGroupIds: Set<String>,
    private val currentUserId: String?
) : RecyclerView.Adapter<RoomsAdapter.ViewHolder>() {

    var onGroupClickListener: ((Group, Boolean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        val isJoined = joinedGroupIds.contains(group.id)
        val isOwner = group.created_by == currentUserId

        android.util.Log.d("RoomsAdapter", "Binding group: ${group.name}, id: ${group.id}, isJoined: $isJoined, isOwner: $isOwner, joinedIds: $joinedGroupIds")

        holder.nameTextView.text = group.name

        // Show status if joined, show action if not joined
        if (isJoined) {
            holder.statusTextView.visibility = View.VISIBLE
            holder.statusTextView.text = if (isOwner) "Owner" else "Joined"
            holder.actionTextView.visibility = View.GONE
        } else {
            holder.statusTextView.visibility = View.GONE
            holder.actionTextView.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            onGroupClickListener?.invoke(group, isJoined)
        }
    }

    override fun getItemCount() = groups.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val statusTextView: TextView = itemView.findViewById(R.id.status_text_view)
        val actionTextView: TextView = itemView.findViewById(R.id.action_text_view)
    }
}
