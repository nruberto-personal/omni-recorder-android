package com.nruberto.omnirecorder.shared

import kotlinx.serialization.Serializable

@Serializable
data class Recording(
    val id: String,
    val recordedAtEpochMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
)
