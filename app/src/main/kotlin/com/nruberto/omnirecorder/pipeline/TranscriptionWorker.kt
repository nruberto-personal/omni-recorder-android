package com.nruberto.omnirecorder.pipeline

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nruberto.omnirecorder.providers.TranscriptionProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val provider: TranscriptionProvider,
    private val speakerInference: SpeakerInference,
    private val store: TranscriptStore,
    private val repository: RecordingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return Result.failure()
        val audioUri = repository.audioUriFor(recordingId)
        if (audioUri == null) {
            Log.e(TAG, "audio URI not found for $recordingId")
            return Result.failure()
        }

        // Audio lives in shared storage as a content URI. Copy to cacheDir so providers
        // can use File-based multipart/body uploads without special URI handling.
        val tempFile = File(applicationContext.cacheDir, "upload_$recordingId.m4a")
        try {
            applicationContext.contentResolver.openInputStream(audioUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return Result.failure().also { Log.e(TAG, "openInputStream returned null") }

            Log.d(TAG, "transcribing $recordingId (${tempFile.length()} bytes) via ${provider.name}")
            val raw = provider.transcribe(tempFile, recordingId)
            val transcript = speakerInference.inferIfNeeded(raw)
            store.save(recordingId, transcript)
            repository.triggerRefresh()
            Log.d(TAG, "transcribed $recordingId (${transcript.segments.size} segments, ${transcript.rawSpeakers.size} speakers)")
            return Result.success()
        } catch (e: Throwable) {
            Log.e(TAG, "transcription failed for $recordingId (attempt $runAttemptCount)", e)
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            tempFile.delete()
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recordingId"
        const val MAX_RETRIES = 3
        private const val TAG = "OmniRecTranscribe"
        private const val WORK_NAME_PREFIX = "transcribe_"
        fun workName(recordingId: String) = "$WORK_NAME_PREFIX$recordingId"
    }
}
