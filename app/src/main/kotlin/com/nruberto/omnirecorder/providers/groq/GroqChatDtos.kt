package com.nruberto.omnirecorder.providers.groq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqChatMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: GroqResponseFormat,
)

@Serializable
data class GroqChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class GroqResponseFormat(
    val type: String,
)

@Serializable
data class GroqChatResponse(
    val choices: List<GroqChatChoice> = emptyList(),
)

@Serializable
data class GroqChatChoice(
    val message: GroqChatResponseMessage,
)

@Serializable
data class GroqChatResponseMessage(
    val content: String,
)
