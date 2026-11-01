package com.adl.cafe.data.remote

import com.adl.cafe.data.model.DrinkSize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The menu assistant, as [com.adl.cafe.data.CafeRepository] sees it: the `chat`
 * Edge Function in `supabase/functions/chat`. An interface so the repository
 * tests can stand in a fake instead of calling the model.
 */
interface ChatRemote {

    /**
     * The assistant's next turn for [messages], oldest first and ending with
     * the customer's. Throws if the assistant could not be reached.
     */
    suspend fun send(messages: List<ChatMessage>): ChatResponse
}

@Serializable
enum class ChatRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT
}

@Serializable
data class ChatMessage(val role: ChatRole, val text: String)

/** What the `chat` function returns. */
@Serializable
data class ChatResponse(
    val reply: String,
    val suggestions: List<SuggestedLine> = emptyList()
)

/** Ids, sizes and quantities only: prices always come from the menu. */
@Serializable
data class SuggestedLine(
    @SerialName("item_id") val menuItemId: Long,
    val size: DrinkSize,
    val quantity: Int
)
