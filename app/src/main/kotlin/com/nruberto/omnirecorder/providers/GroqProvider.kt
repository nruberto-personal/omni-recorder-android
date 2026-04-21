package com.nruberto.omnirecorder.providers

import com.nruberto.omnirecorder.providers.groq.GroqApi
import com.nruberto.omnirecorder.shared.Transcript
import com.nruberto.omnirecorder.shared.TranscriptSegment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groq Whisper-large-v3 transcription. No diarization — assigns all output to a single
 * speaker. Intended as a speed-critical text-only alternate; Deepgram is the primary path.
 */
@Singleton
class GroqProvider @Inject constructor(
    private val api: GroqApi,
) : TranscriptionProvider {

    override val name: String = "groq-whisper-large-v3"

    override suspend fun transcribe(audio: File, recordingId: String): Transcript {
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = audio.name,
            body = audio.asRequestBody("audio/mp4".toMediaType()),
        )
        val modelPart = "whisper-large-v3".toRequestBody("text/plain".toMediaType())
        val formatPart = "verbose_json".toRequestBody("text/plain".toMediaType())

        val response = api.transcribe(filePart, modelPart, formatPart)

        val singleSpeaker = "SPEAKER_00"
        val segments = response.segments.map {
            TranscriptSegment(
                start = it.start,
                end = it.end,
                text = it.text.trim(),
                speaker = singleSpeaker,
            )
        }

        return Transcript(
            audioFilename = audio.name,
            modelId = name,
            language = response.language,
            createdAt = Instant.now().toString(),
            fullText = response.text.trim(),
            segments = segments,
            words = null,
            rawSpeakers = if (segments.isEmpty()) emptyList() else listOf(singleSpeaker),
            speakerNames = emptyMap(),
        )
    }
}
