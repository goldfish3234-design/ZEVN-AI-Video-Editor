package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live colour preview. While the Adjust panel is open, the frame under the playhead is shown here
 * with the current colour settings applied. It updates as the sliders move.
 * (The video itself keeps playing underneath; this is a still frame for checking colours.)
 */
@Composable
fun ColorPreview(
    sources: List<Source>,
    segments: List<Segment>,
    playheadMs: Long,
    adjust: VideoAdjust,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (index, offset) = TimelineModel.locate(playheadMs)
    val seg = segments.getOrNull(index)
    val srcMs = if (seg == null) 0L else seg.startMs + (offset * seg.speed).toLong()
    // reload the still only when the playhead has moved a little (every 250 ms)
    val key = if (seg == null) null else "${seg.source}|${srcMs / 250L}"

    var base by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        if (seg != null) {
            base = withContext(Dispatchers.IO) { frameAt(context, sources[seg.source].uri, srcMs) }
        }
    }

    val shown = remember(base, adjust) { base?.let { applyColours(it, adjust) } }
    if (shown != null) {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = "Colour preview",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier)
    }
}

/** One frame from the file at [ms], scaled down so that the colour preview stays fast. */
private fun frameAt(context: Context, uri: Uri, ms: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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

/** Applies the same colour transform that the export uses. */
private fun applyColours(src: Bitmap, adjust: VideoAdjust): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(previewColorMatrix(adjust))
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}
