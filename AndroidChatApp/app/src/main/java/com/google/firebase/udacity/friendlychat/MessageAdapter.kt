package com.google.firebase.udacity.friendlychat

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions

class MessageAdapter(options: FirebaseRecyclerOptions<FriendlyMessage>) : FirebaseRecyclerAdapter<FriendlyMessage, MessageAdapter.ChatHolder>(options) {

    override fun onBindViewHolder(holder: ChatHolder, position: Int, message: FriendlyMessage) {

        val isPhoto = message.photoUrl != null
        if (isPhoto) {
            holder.messageTextView.visibility = View.GONE
            holder.photoImageView.visibility = View.VISIBLE
            Glide.with(holder.photoImageView.context)
                    .load(message.photoUrl)
                    .into(holder.photoImageView)
        } else {
            holder.messageTextView.visibility = View.VISIBLE
            holder.photoImageView.visibility = View.GONE
            holder.messageTextView.text = message.text
        }
        holder.authorTextView.text = message.name
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatHolder(view)
    }

    class ChatHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var photoImageView: ImageView
        var messageTextView: TextView
        var authorTextView: TextView

        init {
            photoImageView = itemView.findViewById(R.id.photoImageView)
            messageTextView = itemView.findViewById(R.id.messageTextView)
            authorTextView = itemView.findViewById(R.id.nameTextView)
        }
    }
}
