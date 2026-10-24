package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the transition on the preview while the playhead is inside it.
 * Dissolve: the last frame of the clip before fades out over the new clip.
 * Fade from black / Flash white: a black or white layer fades out.
 */
@Composable
fun TransitionPreview(
    sources: List<Source>,
    segments: List<Segment>,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // which clip the playhead is in, and where that clip starts on the timeline
    var acc = 0L
    var idx = -1
    var startTl = 0L
    for ((i, s) in segments.withIndex()) {
        if (playheadMs >= acc && playheadMs < acc + s.durationMs) {
            idx = i
            startTl = acc
            break
        }
        acc += s.durationMs
    }
    val seg = segments.getOrNull(idx)
    val prev = segments.getOrNull(idx - 1)
    val type = if (seg != null && idx > 0) TransitionStore.get(seg) else "none"
    val local = playheadMs - startTl
    val inWindow = type != "none" && local >= 0L && local < TransitionStore.LENGTH_MS
    val fade = 1f - (local.toFloat() / TransitionStore.LENGTH_MS).coerceIn(0f, 1f)

    // the last frame of the clip before, used by Dissolve
    val prevKey = if (type == "dissolve" && prev != null) "${prev.source}|${prev.endMs}" else ""
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(prevKey) {
        if (prev != null && prevKey.isNotEmpty()) {
            lastFrame = withContext(Dispatchers.IO) {
                frameAt(context, sources[prev.source].uri, prev.endMs - 40L)
            }
        } else {
            lastFrame = null
        }
    }

    if (!inWindow) return
    when (type) {
        "flash" -> Box(modifier = modifier.background(Color.White.copy(alpha = fade)))
        "fade_black" -> Box(modifier = modifier.background(Color.Black.copy(alpha = fade)))
        "dissolve" -> {
            val frame = lastFrame
            if (frame != null) {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "Transition",
                    contentScale = ContentScale.Crop,
                    modifier = modifier.alpha(fade)
                )
            }
        }
    }
}

/** One frame of the file at [ms], scaled down for the preview. */
private fun frameAt(context: Context, uri: Uri, ms: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(ms.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (frame == null || frame.width <= 0) {
            null
        } else {
            val w = 540
            val h = (frame.height.toFloat() * w / frame.width.toFloat()).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, w, h, true)
        }
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}
