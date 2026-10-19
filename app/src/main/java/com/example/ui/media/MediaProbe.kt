package com.example.ui.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Reads the duration of a picked media file. Call from a background thread. */
object MediaProbe {
    fun durationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
