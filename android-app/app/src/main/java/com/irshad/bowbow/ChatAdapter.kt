package com.irshad.bowbow

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.irshad.bowbow.databinding.ItemMessageBinding

data class ChatMessage(val text: String, val isUser: Boolean)

/** Simple two-sided chat list: user messages right/blue, BOW BOW left/grey. */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.messageText.text = message.text
            val context = binding.root.context
            val params = binding.messageText.layoutParams as LinearLayout.LayoutParams
            if (message.isUser) {
                params.gravity = Gravity.END
                binding.messageText.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.user_bubble)
                )
                binding.messageText.setTextColor(
                    ContextCompat.getColor(context, android.R.color.white)
                )
            } else {
                params.gravity = Gravity.START
                binding.messageText.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.assistant_bubble)
                )
                binding.messageText.setTextColor(
                    ContextCompat.getColor(context, android.R.color.black)
                )
            }
            binding.messageText.layoutParams = params
        }
    }
}
