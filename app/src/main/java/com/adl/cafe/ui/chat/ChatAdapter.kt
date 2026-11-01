package com.adl.cafe.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.data.remote.ChatRole
import com.adl.cafe.databinding.ItemChatAssistantBinding
import com.adl.cafe.databinding.ItemChatUserBinding

/** An entry plus its place in the conversation, which suggestion keys need. */
data class ChatRow(val index: Int, val entry: ChatEntry, val addedPositions: Set<Int>)

/** The conversation: the customer's bubbles on the right, the assistant's on the left. */
class ChatAdapter(
    private val onAdd: (SuggestionKey) -> Unit
) : ListAdapter<ChatRow, RecyclerView.ViewHolder>(DIFF) {

    class UserViewHolder(
        private val binding: ItemChatUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ChatRow) {
            binding.textMessage.text = row.entry.text
        }
    }

    inner class AssistantViewHolder(
        private val binding: ItemChatAssistantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var index = RecyclerView.NO_POSITION
        private val suggestions = SuggestionAdapter { position -> onAdd(SuggestionKey(index, position)) }

        init {
            binding.recyclerSuggestions.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = suggestions
            }
        }

        fun bind(row: ChatRow) {
            index = row.index
            binding.textMessage.text = row.entry.text
            suggestions.submitList(
                row.entry.suggestions.mapIndexed { position, suggestion ->
                    SuggestionCard(position, suggestion, position in row.addedPositions)
                }
            )
            binding.recyclerSuggestions.visibility =
                if (row.entry.suggestions.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).entry.role == ChatRole.USER) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(ItemChatUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantViewHolder(ItemChatAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(getItem(position))
            is AssistantViewHolder -> holder.bind(getItem(position))
        }
    }

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_ASSISTANT = 1

        val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(old: ChatRow, new: ChatRow) = old.index == new.index
            override fun areContentsTheSame(old: ChatRow, new: ChatRow) = old == new
        }
    }
}
