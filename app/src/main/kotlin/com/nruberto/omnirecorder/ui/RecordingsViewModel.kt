package com.nruberto.omnirecorder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nruberto.omnirecorder.pipeline.RecordingsRepository
import com.nruberto.omnirecorder.shared.Recording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repository: RecordingsRepository,
) : ViewModel() {
    val recordings: StateFlow<List<Recording>> = repository.recordings

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }
}
