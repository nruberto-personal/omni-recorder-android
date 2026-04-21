package com.nruberto.omnirecorder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nruberto.omnirecorder.pipeline.TranscriptStore
import com.nruberto.omnirecorder.pipeline.TranscriptionWorker
import com.nruberto.omnirecorder.providers.TranscriptionProvider
import com.nruberto.omnirecorder.shared.Transcript
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TranscriptState {
    data object Loading : TranscriptState
    data class Transcribing(val provider: String) : TranscriptState
    data class Ready(val transcript: Transcript) : TranscriptState
    data class Error(val message: String) : TranscriptState
}

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: TranscriptStore,
    private val provider: TranscriptionProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<TranscriptState>(TranscriptState.Loading)
    val state: StateFlow<TranscriptState> = _state.asStateFlow()

    fun load(recordingId: String) {
        viewModelScope.launch {
            val existing = store.load(recordingId)
            if (existing != null) {
                _state.value = TranscriptState.Ready(existing)
            } else {
                enqueueAndObserve(recordingId)
            }
        }
    }

    fun retry(recordingId: String) {
        viewModelScope.launch { enqueueAndObserve(recordingId) }
    }

    private suspend fun enqueueAndObserve(recordingId: String) {
        _state.value = TranscriptState.Transcribing(provider.name)

        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_RECORDING_ID to recordingId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(
            TranscriptionWorker.workName(recordingId),
            ExistingWorkPolicy.KEEP,
            request,
        )

        wm.getWorkInfosForUniqueWorkFlow(TranscriptionWorker.workName(recordingId))
            .collect { infos ->
                val info = infos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val transcript = store.load(recordingId)
                        _state.value = if (transcript != null) {
                            TranscriptState.Ready(transcript)
                        } else {
                            TranscriptState.Error("transcript missing after success")
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _state.value = TranscriptState.Error("transcription failed")
                    }
                    WorkInfo.State.CANCELLED -> {
                        _state.value = TranscriptState.Error("transcription cancelled")
                    }
                    else -> {
                        // RUNNING / ENQUEUED / BLOCKED — stay in Transcribing state
                    }
                }
            }
    }
}
