package com.nruberto.omnirecorder.providers.groq

import kotlinx.serialization.Serializable

@Serializable
data class GroqTranscriptionResponse(
    val text: String,
    val language: String? = null,
    val duration: Double? = null,
    val segments: List<GroqSegment> = emptyList(),
)

@Serializable
data class GroqSegment(
    val start: Double,
    val end: Double,
    val text: String,
)
