package com.mrrob.llmchat.data

/** A single entry in a conversation transcript. */
data class ChatMessage(
    val role: Role,
    val text: String,
    val id: Long = System.nanoTime()
) {
    enum class Role { USER, ASSISTANT }
}
