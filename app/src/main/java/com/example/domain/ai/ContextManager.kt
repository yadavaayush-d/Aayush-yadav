package com.example.domain.ai

import com.example.data.local.entity.MessageEntity

/**
 * Context Manager for Nexa AI Chat.
 *
 * Ensures conversations pass appropriate historical turns to the AI engine
 * while maintaining a strict character/token budget to avoid excessive costs,
 * timeouts, or rate limits.
 */
class ContextManager(
    private val maxRecentMessages: Int = 10,
    private val maxCharacterBudget: Int = 8000
) {
    /**
     * Sanitizes and selects recent conversational turns (user & assistant)
     * adhering to budget bounds in chronological order.
     */
    fun buildContext(history: List<MessageEntity>): List<AIMessage> {
        val conversational = history.filter { it.role == "user" || it.role == "assistant" }
        val recent = conversational.takeLast(maxRecentMessages)

        var totalChars = 0
        val selected = mutableListOf<MessageEntity>()

        // Traverse backwards from newest to preserve recent context first
        for (msg in recent.reversed()) {
            if (totalChars + msg.content.length <= maxCharacterBudget) {
                selected.add(msg)
                totalChars += msg.content.length
            } else {
                break
            }
        }

        // Return back in chronological order
        return selected.reversed().map {
            AIMessage(
                role = it.role,
                content = it.content
            )
        }
    }
}
