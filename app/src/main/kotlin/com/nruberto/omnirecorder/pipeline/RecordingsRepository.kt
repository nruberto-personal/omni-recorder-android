package com.nruberto.omnirecorder.pipeline

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.nruberto.omnirecorder.shared.Recording
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surfaces audio files from the phone's shared storage. The Galaxy Watch's Samsung Voice
 * Recorder auto-syncs recordings to the paired Samsung phone, which lands them in the
 * MediaStore. We observe MediaStore for changes so the list stays live.
 */
@Singleton
class RecordingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            triggerRefresh()
        }
    }

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        triggerRefresh()
    }

    fun triggerRefresh() {
        scope.launch { refresh() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _recordings.value = queryAudio()
    }

    /** Resolve the current MediaStore URI for a recording, by display name. */
    fun audioUriFor(recordingId: String): Uri? {
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("$recordingId.%")
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id,
                )
            }
        }
        return null
    }

    private fun queryAudio(): List<Recording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            /* selection = */ null,
            /* selectionArgs = */ null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        ) ?: return emptyList()

        val out = mutableListOf<Recording>()
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val durIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                out.add(
                    Recording(
                        id = name.substringBeforeLast("."),
                        recordedAtEpochMs = it.getLong(dateIdx) * 1000L,
                        durationMs = it.getLong(durIdx),
                        sizeBytes = it.getLong(sizeIdx),
                    ),
                )
            }
        }
        return out
    }
}
