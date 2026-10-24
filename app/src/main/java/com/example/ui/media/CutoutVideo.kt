package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Frames of a video, saved as PNG files. They play back as an animated picture on the timeline.
 * With [removeBg] on, the background of each frame is removed (any subject) and the frames are transparent.
 * With it off, the frames are normal pictures (used for video picture-in-picture).
 * Call from a background thread.
 */
object CutoutVideo {

    /** Frames per second of the animation (one frame every 100 ms). */
    const val FPS = 10f
    private const val FRAME_MS = 100L

    // Frames are made smaller, so processing and export stay quick
    private const val MAX_SIDE = 480

    // A clip is limited to this length, to keep processing time and storage reasonable
    const val MAX_SECONDS = 10

    /**
     * Frames of the video [uri] from [startMs] for up to [MAX_SECONDS].
     * [onProgress] receives 0..100. Returns the PNG file addresses, or null on failure.
     */
    fun generate(
        context: Context,
        uri: Uri,
        startMs: Long,
        durationMs: Long,
        removeBg: Boolean = true,
        onProgress: (Int) -> Unit
    ): List<String>? {
        val lengthMs = minOf(durationMs, MAX_SECONDS * 1000L)
        val count = (lengthMs / FRAME_MS).toInt()
        if (count <= 0) return null

        val dir = File(context.filesDir, "frames/${System.currentTimeMillis()}").apply { mkdirs() }
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<String>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until count) {
                val tMs = startMs + i * FRAME_MS
                val raw = retriever.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val small = scaleDown(raw)
                    val out: Bitmap? = if (removeBg) BackgroundRemover.removeFromBitmap(small) else small
                    if (out != null) {
                        val f = File(dir, "f_%04d.png".format(i))
                        FileOutputStream(f).use { stream -> out.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                        frames.add(Uri.fromFile(f).toString())
                    }
                }
                // a frame that could not be read keeps the previous one, so the timing stays right
                if (frames.size <= i && frames.isNotEmpty()) {
                    frames.add(frames.last())
                }
                onProgress(((i + 1) * 100) / count)
            }
        } catch (e: Exception) {
            return null
        } finally {
            retriever.release()
        }
        return if (frames.isEmpty()) null else frames
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= MAX_SIDE) return src
        val scale = MAX_SIDE.toFloat() / maxSide
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
