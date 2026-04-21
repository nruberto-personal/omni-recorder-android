package com.nruberto.omnirecorder.pipeline

import android.content.Context
import com.nruberto.omnirecorder.shared.Transcript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun save(recordingId: String, transcript: Transcript) = withContext(Dispatchers.IO) {
        file(recordingId).writeText(json.encodeToString(transcript))
    }

    suspend fun load(recordingId: String): Transcript? = withContext(Dispatchers.IO) {
        val f = file(recordingId)
        if (!f.exists()) null
        else runCatching { json.decodeFromString<Transcript>(f.readText()) }.getOrNull()
    }

    fun exists(recordingId: String): Boolean = file(recordingId).exists()

    private fun file(recordingId: String): File {
        val dir = File(context.filesDir, "transcripts").apply { mkdirs() }
        return File(dir, "$recordingId.json")
    }
}
