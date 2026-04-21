package com.nruberto.omnirecorder.shared

import kotlinx.serialization.Serializable

@Serializable
data class Transcript(
    val audioFilename: String,
    val modelId: String,
    val language: String?,
    val createdAt: String,
    val fullText: String,
    val segments: List<TranscriptSegment>,
    val words: List<TranscriptWord>? = null,
    val rawSpeakers: List<String>,
    val speakerNames: Map<String, String>,
)

@Serializable
data class TranscriptSegment(
    val start: Double,
    val end: Double,
    val text: String,
    val speaker: String,
)

@Serializable
data class TranscriptWord(
    val start: Double,
    val end: Double,
    val text: String,
    val probability: Double? = null,
)
