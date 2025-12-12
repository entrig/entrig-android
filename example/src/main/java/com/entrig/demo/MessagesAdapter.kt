package com.entrig.demo

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessagesAdapter(
    private val messages: List<MessageWithUser>,
    private val currentUserId: String
) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        val isMe = message.user_id == currentUserId

        holder.messageTextView.text = message.content

        // Show sender name for other users
        if (!isMe) {
            holder.senderNameTextView.visibility = View.VISIBLE
            holder.senderNameTextView.text = message.userName
        } else {
            holder.senderNameTextView.visibility = View.GONE
        }

        // Style message bubble
        val bubble = GradientDrawable()
        bubble.cornerRadii = if (isMe) {
            floatArrayOf(50f, 50f, 10f, 10f, 50f, 50f, 50f, 50f) // topLeft, topRight, bottomRight, bottomLeft
        } else {
            floatArrayOf(10f, 10f, 50f, 50f, 50f, 50f, 50f, 50f)
        }

        if (isMe) {
            bubble.setColor(Color.parseColor("#2196F3")) // Blue for sent
            holder.messageTextView.setTextColor(Color.WHITE)
            holder.messageContainer.gravity = Gravity.END
            (holder.messageContainer.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = dpToPx(60)
                rightMargin = dpToPx(8)
            }
        } else {
            bubble.setColor(Color.parseColor("#EEEEEE")) // Gray for received
            holder.messageTextView.setTextColor(Color.parseColor("#212121"))
            holder.messageContainer.gravity = Gravity.START
            (holder.messageContainer.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = dpToPx(8)
                rightMargin = dpToPx(60)
            }
        }

        holder.messageBubble.background = bubble
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * 2.75).toInt()
    }

    override fun getItemCount() = messages.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageContainer: LinearLayout = itemView.findViewById(R.id.message_container)
        val senderNameTextView: TextView = itemView.findViewById(R.id.sender_name_text_view)
        val messageBubble: View = itemView.findViewById(R.id.message_bubble)
        val messageTextView: TextView = itemView.findViewById(R.id.message_text_view)
    }
}
