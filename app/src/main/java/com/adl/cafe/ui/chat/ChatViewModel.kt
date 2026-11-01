package com.adl.cafe.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.model.Suggestion
import com.adl.cafe.data.remote.ChatMessage
import com.adl.cafe.data.remote.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble in the conversation; only the assistant's carry suggestions. */
data class ChatEntry(
    val role: ChatRole,
    val text: String,
    val suggestions: List<Suggestion> = emptyList()
)

/** A suggestion's place in the conversation: which entry, which card. */
data class SuggestionKey(val entry: Int, val position: Int)

data class ChatUiState(
    val entries: List<ChatEntry> = emptyList(),
    val isSending: Boolean = false,
    /** The last request failed; the customer's message is still the last entry. */
    val failed: Boolean = false,
    /** Suggestions already added to the cart, so their cards show a tick. */
    val added: Set<SuggestionKey> = emptySet()
)

/**
 * The Ask sheet's conversation. Owned by the Menu screen rather than the sheet,
 * so closing the sheet and opening it again picks up where it left off.
 */
class ChatViewModel(private val repository: CafeRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(entries = it.entries + ChatEntry(ChatRole.USER, message)) }
        requestReply()
    }

    fun retry() {
        if (_state.value.failed && !_state.value.isSending) requestReply()
    }

    fun add(key: SuggestionKey) {
        val state = _state.value
        if (key in state.added) return
        val suggestion = state.entries.getOrNull(key.entry)?.suggestions?.getOrNull(key.position)
            ?: return
        // Marked first, so a second tap before the write lands cannot add it twice.
        _state.update { it.copy(added = it.added + key) }
        viewModelScope.launch {
            repository.addToCart(suggestion.item.id, suggestion.size, suggestion.quantity)
        }
    }

    private fun requestReply() {
        _state.update { it.copy(isSending = true, failed = false) }
        val conversation = _state.value.entries.map { ChatMessage(it.role, it.text) }
        viewModelScope.launch {
            try {
                val reply = repository.askAssistant(conversation)
                _state.update {
                    it.copy(
                        entries = it.entries + ChatEntry(ChatRole.ASSISTANT, reply.text, reply.suggestions),
                        isSending = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline, or the function is not deployed: offer a retry, keep the message.
                Log.w(TAG, "Assistant request failed", e)
                _state.update { it.copy(isSending = false, failed = true) }
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
