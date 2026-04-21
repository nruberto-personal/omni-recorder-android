package com.nruberto.omnirecorder.providers.deepgram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepgramResponse(
    val results: DeepgramResults = DeepgramResults(),
)

@Serializable
data class DeepgramResults(
    val channels: List<DeepgramChannel> = emptyList(),
)

@Serializable
data class DeepgramChannel(
    val alternatives: List<DeepgramAlternative> = emptyList(),
    @SerialName("detected_language") val detectedLanguage: String? = null,
)

@Serializable
data class DeepgramAlternative(
    val transcript: String = "",
    val words: List<DeepgramWord> = emptyList(),
)

@Serializable
data class DeepgramWord(
    val word: String,
    @SerialName("punctuated_word") val punctuatedWord: String? = null,
    val start: Double,
    val end: Double,
    val confidence: Double? = null,
    val speaker: Int? = null,
)
