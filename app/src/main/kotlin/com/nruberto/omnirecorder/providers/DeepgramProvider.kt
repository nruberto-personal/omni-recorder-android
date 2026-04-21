package com.nruberto.omnirecorder.providers

import com.nruberto.omnirecorder.providers.deepgram.DeepgramApi
import com.nruberto.omnirecorder.providers.deepgram.DeepgramWord
import com.nruberto.omnirecorder.shared.Transcript
import com.nruberto.omnirecorder.shared.TranscriptSegment
import com.nruberto.omnirecorder.shared.TranscriptWord
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deepgram nova-2-conversationalai transcription + built-in diarization.
 * Returns speaker-labeled segments by grouping consecutive same-speaker words.
 * Ignores paragraph-level speaker labels (they collapse mid-paragraph speaker changes).
 */
@Singleton
class DeepgramProvider @Inject constructor(
    private val api: DeepgramApi,
) : TranscriptionProvider {

    override val name: String = "deepgram-nova-2-conversationalai"

    override suspend fun transcribe(audio: File, recordingId: String): Transcript {
        val body = audio.asRequestBody("audio/mp4".toMediaType())
        val response = api.listen(audio = body)

        val channel = response.results.channels.firstOrNull()
        val alt = channel?.alternatives?.firstOrNull()
        val words = alt?.words.orEmpty()

        val segments = groupByWord(words)
        val rawSpeakers = segments.map { it.speaker }.distinct()
        val allWords = words.map { w ->
            TranscriptWord(
                start = w.start,
                end = w.end,
                text = w.punctuatedWord ?: w.word,
                probability = w.confidence,
            )
        }

        return Transcript(
            audioFilename = audio.name,
            modelId = name,
            language = channel?.detectedLanguage ?: "en",
            createdAt = Instant.now().toString(),
            fullText = alt?.transcript.orEmpty(),
            segments = segments,
            words = allWords,
            rawSpeakers = rawSpeakers,
            speakerNames = emptyMap(),
        )
    }

    private fun groupByWord(words: List<DeepgramWord>): List<TranscriptSegment> {
        if (words.isEmpty()) return emptyList()

        val segments = mutableListOf<TranscriptSegment>()
        var groupStart = 0
        var currentSpeaker = words[0].speaker ?: 0

        for (i in 1..words.size) {
            val atEnd = i == words.size
            val speakerChanged = !atEnd && (words[i].speaker ?: 0) != currentSpeaker

            if (atEnd || speakerChanged) {
                val group = words.subList(groupStart, i)
                segments.add(
                    TranscriptSegment(
                        start = group.first().start,
                        end = group.last().end,
                        text = group.joinToString(" ") { it.punctuatedWord ?: it.word },
                        speaker = "SPEAKER_%02d".format(currentSpeaker),
                    ),
                )
                if (!atEnd) {
                    groupStart = i
                    currentSpeaker = words[i].speaker ?: 0
                }
            }
        }
        return segments
    }
}
