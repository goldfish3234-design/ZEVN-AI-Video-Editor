package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Cut-out video: takes frames of a video, removes the background of each frame (any subject),
 * and saves them as transparent PNG files. The frames play back as an animated sticker on top
 * of the timeline. Call from a background thread.
 */
object CutoutVideo {

    /** Frames per second of the cut-out animation (one frame every 100 ms). */
    const val FPS = 10f
    private const val FRAME_MS = 100L

    // Frames are made smaller, so processing and export stay quick
    private const val MAX_SIDE = 480

    // A cut-out is limited to this length, to keep processing time and storage reasonable
    const val MAX_SECONDS = 10

    /**
     * Cuts out the subject of the video [uri] from [startMs] for up to [MAX_SECONDS].
     * [onProgress] receives 0..100. Returns the list of PNG file addresses, or null on failure.
     */
    fun generate(
        context: Context,
        uri: Uri,
        startMs: Long,
        durationMs: Long,
        onProgress: (Int) -> Unit
    ): List<String>? {
        val lengthMs = minOf(durationMs, MAX_SECONDS * 1000L)
        val count = (lengthMs / FRAME_MS).toInt()
        if (count <= 0) return null

        val dir = File(context.filesDir, "cutouts/${System.currentTimeMillis()}").apply { mkdirs() }
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<String>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until count) {
                val tMs = startMs + i * FRAME_MS
                val raw = retriever.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val small = scaleDown(raw)
                    val cut = BackgroundRemover.removeFromBitmap(small)
                    if (cut != null) {
                        val f = File(dir, "f_%04d.png".format(i))
                        FileOutputStream(f).use { out -> cut.compress(Bitmap.CompressFormat.PNG, 100, out) }
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
