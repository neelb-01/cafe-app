package com.adl.cafe.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [ChatRemote] over the `chat` Edge Function. The function holds the model's
 * API key, so the app only ever sends it the publishable key.
 */
class SupabaseChatRemote(private val client: SupabaseClient) : ChatRemote {

    override suspend fun send(messages: List<ChatMessage>): ChatResponse {
        val response = client.functions.invoke(
            function = FUNCTION_CHAT,
            body = ChatRequest(messages),
            headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
        )
        return json.decodeFromString<ChatResponse>(response.bodyAsText())
    }

    private companion object {
        const val FUNCTION_CHAT = "chat"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class ChatRequest(val messages: List<ChatMessage>)
