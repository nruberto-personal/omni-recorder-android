package com.nruberto.omnirecorder.providers

import com.nruberto.omnirecorder.shared.Transcript
import java.io.File

interface TranscriptionProvider {
    val name: String
    suspend fun transcribe(audio: File, recordingId: String): Transcript
}
