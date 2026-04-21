package com.nruberto.omnirecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nruberto.omnirecorder.shared.Transcript
import com.nruberto.omnirecorder.shared.TranscriptSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    recordingId: String,
    onBack: () -> Unit,
    viewModel: TranscriptViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(recordingId) { viewModel.load(recordingId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recordingId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (val s = state) {
                TranscriptState.Loading -> CenteredSpinner()
                is TranscriptState.Transcribing -> CenteredMessage {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Transcribing with ${s.provider}…")
                }
                is TranscriptState.Ready -> TranscriptContent(s.transcript)
                is TranscriptState.Error -> CenteredMessage {
                    Text("Transcription failed")
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry(recordingId) }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun TranscriptContent(transcript: Transcript) {
    if (transcript.segments.isEmpty()) {
        Text(
            text = transcript.fullText.ifBlank { "(empty transcript)" },
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = transcript.segments,
            key = { seg -> "${seg.start}-${seg.speaker}" },
        ) { segment ->
            SegmentRow(segment, transcript.rawSpeakers, transcript.speakerNames)
        }
    }
}

@Composable
private fun SegmentRow(
    segment: TranscriptSegment,
    rawSpeakers: List<String>,
    speakerNames: Map<String, String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${formatTimestamp(segment.start)}  ${displayName(segment.speaker, rawSpeakers, speakerNames)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = segment.text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun displayName(
    rawId: String,
    rawSpeakers: List<String>,
    speakerNames: Map<String, String>,
): String {
    speakerNames[rawId]?.takeIf { it.isNotBlank() }?.let { return it }
    val index = rawSpeakers.indexOf(rawId)
    if (index < 0) return rawId
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return if (index < letters.length) "Speaker ${letters[index]}" else "Speaker ${index + 1}"
}

private fun formatTimestamp(seconds: Double): String {
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
