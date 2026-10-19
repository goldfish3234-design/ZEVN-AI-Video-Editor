package com.example.ui.media

import android.content.Context
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.math.min

/**
 * Renders the timeline to an MP4 file.
 * Video segments play one after another; background music is mixed in and repeated
 * until the video ends. Must be started from the main thread.
 */
class TimelineExporter(private val context: Context) {

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()

    val isRunning: Boolean
        get() = transformer != null

    /** Progress 0..100 while exporting. */
    fun progress(): Int {
        val t = transformer ?: return 0
        t.getProgress(progressHolder)
        return progressHolder.progress.coerceIn(0, 100)
    }

    fun export(
        sources: List<Source>,
        segments: List<Segment>,
        music: MusicTrack?,
        onDone: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (segments.isEmpty() || transformer != null) {
            onError("Nothing to export")
            return
        }

        val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ZEVN")
        outDir.mkdirs()
        val outFile = File(outDir, "zevn_${System.currentTimeMillis()}.mp4")

        // Video: one edited item per segment, in timeline order
        val videoItems = segments.map { seg ->
            val clipped = MediaItem.Builder()
                .setUri(sources[seg.source].uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(seg.startMs)
                        .setEndPositionMs(seg.endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(clipped).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).build())

        // Music: repeated clips that cover the whole timeline length
        if (music != null && music.durationMs > 0L) {
            val totalMs = segments.sumOf { it.durationMs }
            val musicItems = mutableListOf<EditedMediaItem>()
            var remaining = totalMs
            while (remaining > 0L) {
                val len = min(music.durationMs, remaining)
                val clipped = MediaItem.Builder()
                    .setUri(music.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0L)
                            .setEndPositionMs(len)
                            .build()
                    )
                    .build()
                musicItems.add(EditedMediaItem.Builder(clipped).build())
                remaining -= len
            }
            sequences.add(EditedMediaItemSequence.Builder(musicItems).build())
        }

        val composition = Composition.Builder(sequences).build()

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    onDone(outFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer = null
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()

        transformer = t
        t.start(composition, outFile.absolutePath)
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
