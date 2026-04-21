package com.nruberto.omnirecorder.pipeline

import android.util.Log
import com.nruberto.omnirecorder.providers.groq.GroqApi
import com.nruberto.omnirecorder.providers.groq.GroqChatMessage
import com.nruberto.omnirecorder.providers.groq.GroqChatRequest
import com.nruberto.omnirecorder.providers.groq.GroqResponseFormat
import com.nruberto.omnirecorder.shared.Transcript
import com.nruberto.omnirecorder.shared.TranscriptSegment
import com.nruberto.omnirecorder.shared.TranscriptWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groq LLM-based speaker inference. Triggered when acoustic diarization returned 0 or 1
 * speaker despite multi-speaker audio. Ported verbatim from the iOS build's
 * OmniRecorder/Pipeline/SpeakerInference.swift.
 */
@Singleton
class SpeakerInference @Inject constructor(
    private val api: GroqApi,
    private val json: Json,
) {
    suspend fun inferIfNeeded(transcript: Transcript): Transcript {
        if (transcript.rawSpeakers.size > 1) return transcript
        val words = transcript.words.orEmpty()
        if (words.isEmpty()) return transcript

        return try {
            val result = callGroq(words)
            result.reasoning?.let { Log.d(TAG, "reasoning: $it") }
            val speakers = result.turns.map { it.speaker }.toSet()
            Log.d(TAG, "inferred ${result.turns.size} turns, ${speakers.size} speakers: $speakers")
            apply(result, transcript, words)
        } catch (e: Throwable) {
            Log.e(TAG, "inference failed, keeping original transcript", e)
            transcript
        }
    }

    private suspend fun callGroq(words: List<TranscriptWord>): InferredSpeakerTurns {
        val wordList = words.mapIndexed { i, w -> "$i:${w.text.trim()}" }.joinToString(" ")
        val userMessage = "Indexed words:\n$wordList"

        val response = api.chat(
            GroqChatRequest(
                model = MODEL,
                messages = listOf(
                    GroqChatMessage(role = "system", content = SYSTEM_PROMPT),
                    GroqChatMessage(role = "user", content = userMessage),
                ),
                temperature = 0.0,
                responseFormat = GroqResponseFormat(type = "json_object"),
            ),
        )

        val content = response.choices.firstOrNull()?.message?.content
            ?: error("empty choices")
        return json.decodeFromString(content)
    }

    private fun apply(
        inference: InferredSpeakerTurns,
        transcript: Transcript,
        words: List<TranscriptWord>,
    ): Transcript {
        if (inference.turns.isEmpty()) return transcript

        val nameToRawId = mutableMapOf<String, String>()
        val rawSpeakers = mutableListOf<String>()
        val speakerNames = mutableMapOf<String, String>()
        val segments = mutableListOf<TranscriptSegment>()

        for (turn in inference.turns) {
            val startIdx = turn.startWord.coerceAtLeast(0)
            val endIdx = turn.endWord.coerceAtMost(words.size - 1)
            if (startIdx > endIdx) continue

            val turnWords = words.subList(startIdx, endIdx + 1)
            val text = turnWords.joinToString(" ") { it.text.trim() }.trim()
            if (text.isEmpty()) continue

            val rawId = nameToRawId.getOrPut(turn.speaker) {
                val newId = "SPEAKER_%02d".format(nameToRawId.size)
                rawSpeakers.add(newId)
                if (!isGenericLabel(turn.speaker)) {
                    speakerNames[newId] = turn.speaker
                }
                newId
            }

            segments.add(
                TranscriptSegment(
                    start = turnWords.first().start,
                    end = turnWords.last().end,
                    text = text,
                    speaker = rawId,
                ),
            )
        }

        if (segments.isEmpty()) return transcript

        return transcript.copy(
            modelId = transcript.modelId + "+groq",
            segments = segments,
            rawSpeakers = rawSpeakers,
            speakerNames = speakerNames,
        )
    }

    private fun isGenericLabel(name: String): Boolean =
        name.matches(GENERIC_LABEL_RE)

    private companion object {
        const val TAG = "OmniRecInfer"
        const val MODEL = "llama-3.3-70b-versatile"
        val GENERIC_LABEL_RE = Regex("""^Speaker[\s_-]*[\dA-Za-z]$""")
    }
}

@Serializable
private data class InferredSpeakerTurns(
    val reasoning: String? = null,
    val turns: List<InferredTurn> = emptyList(),
)

@Serializable
private data class InferredTurn(
    val speaker: String,
    @SerialName("start_word") val startWord: Int,
    @SerialName("end_word") val endWord: Int,
)

private val SYSTEM_PROMPT = """
    You assign each word of a transcript to a speaker using ONLY conversational context. Acoustic diarization failed, so reason purely from text.

    CRITICAL INFERENCE RULES — apply these in order:

    1. Backward AND forward propagation. Once you determine a speaker is "Nathan" based on any single word range, apply that name to ALL other turns by the same speaker — earlier AND later. Do NOT leave earlier turns generic if you later figured out the name.

    2. "Thanks X" / "Thank you X" means the speaker is ADDRESSING X. So X is NOT the current speaker; X is almost always the OTHER party in a two-person conversation.

    3. "Thank you for having me" / "Happy to be here" / "Great to be here" signals the GUEST. Whoever introduced them earlier is the HOST.

    4. Introduction patterns like "We're here today with X" or "It's X" — the speaker is the HOST introducing X. The HOST's name must be inferred from other cues (e.g. later someone says "Thanks, HOSTNAME").

    5. A sequence like "Thank you X, thank you for having me. Great to be here" is ONE continuous turn from the GUEST. Do NOT split it into multiple turns just because there are multiple sentences.

    6. Prefer FEWER turns. Only introduce a turn boundary when there's a clear conversational pivot (e.g. a direct-address, a question-answer pair, a tonal shift). Over-fragmentation is worse than under-fragmentation.

    7. Every human name mentioned almost certainly belongs to someone in the room. Extract aggressively — do not leave someone as "Speaker 1" if there's any textual evidence for their real name.

    8. **CRITICAL**: if your reasoning identifies real names for any speakers, you MUST use those exact names as the `speaker` value in `turns`. NEVER output "Speaker A", "Speaker B", "Speaker 1", or "Speaker 2" for a speaker whose real name you figured out in reasoning. Before finalizing your output, re-check: does every name mentioned in `reasoning` appear at least once as a speaker value in `turns`?

    EXAMPLE (study this pattern carefully):

    Indexed words:
    0:Hi 1:everyone 2:welcome 3:to 4:the 5:show 6:today 7:I'm 8:here 9:with 10:Alice 11:Hey 12:Bob 13:great 14:to 15:be 16:here

    Correct output:
    {
      "reasoning": "Speaker says 'I'm here with Alice' — introducing Alice. Alice responds 'Hey Bob', revealing the introducer is Bob.",
      "turns": [
        {"speaker": "Bob", "start_word": 0, "end_word": 10},
        {"speaker": "Alice", "start_word": 11, "end_word": 16}
      ]
    }

    Note: Bob's name only appears in Alice's speech (as the addressee), yet we correctly labeled the first speaker as Bob — because whoever Alice is addressing IS the introducer. Apply the same backward inference in YOUR output.

    Output format — return ONLY a JSON object:

    {
      "reasoning": "2-4 sentences. Identify each speaker and cite the exact phrase(s) that revealed their name/role.",
      "turns": [
        {"speaker": "Name", "start_word": 0, "end_word": 12}
      ]
    }

    Coverage rules: every word index 0..N-1 must be covered by exactly one turn. Turns must be contiguous and in chronological order.
""".trimIndent()
