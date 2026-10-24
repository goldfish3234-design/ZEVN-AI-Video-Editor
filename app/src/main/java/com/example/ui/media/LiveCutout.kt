package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live background removal on the preview. The frame under the playhead is cut out (the person or
 * object is kept, the background becomes black). Results are kept in a small cache, so going back
 * over a part of the video is instant. Processing one frame takes a moment on the device.
 */
@Composable
fun LiveCutout(
    sources: List<Source>,
    segments: List<Segment>,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (index, offset) = TimelineModel.locate(playheadMs)
    val seg = segments.getOrNull(index)
    val srcMs = if (seg == null) 0L else seg.startMs + (offset * seg.speed).toLong()
    // a new cut-out every 300 ms of video
    val bucket = srcMs / 300L
    val key = if (seg == null) "" else "${seg.source}|$bucket"

    val cache: SnapshotStateMap<String, Bitmap> = remember { mutableStateMapOf() }
    var message by remember { mutableStateOf("") }
    val current = cache[key]

    LaunchedEffect(key) {
        if (seg != null && cache[key] == null) {
            val uri = sources[seg.source].uri
            val result = withContext(Dispatchers.IO) { cutoutAt(context, uri, srcMs) }
            if (result != null) {
                // keep the cache small
                if (cache.size > 40) cache.remove(cache.keys.first())
                cache[key] = result
                message = ""
            } else {
                message = "Background could not be removed"
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Live cut-out",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = if (message.isEmpty()) "Removing background..." else message,
                color = Color(0xFFA0A0B2),
                fontSize = 12.sp
            )
        }
    }
}

/** One frame at [ms], made smaller, with its background removed. Returns null on failure. */
private fun cutoutAt(context: Context, uri: Uri, ms: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (frame == null || frame.width <= 0 || frame.height <= 0) {
            null
        } else {
            val maxSide = 360f
            val scale = maxSide / maxOf(frame.width, frame.height)
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                frame
            }
            BackgroundRemover.removeFromBitmap(small)
        }
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}
