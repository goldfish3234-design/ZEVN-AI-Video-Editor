package com.example.ui.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
 * Renders the timeline to an MP4 file, then copies it to the public Movies/ZEVN folder
 * so it shows up in Gallery and Files. Must be started from the main thread.
 */
class TimelineExporter(private val context: Context) {

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Progress 0..100 while exporting. */
    fun progress(): Int {
        val t = transformer ?: return 0
        t.getProgress(progressHolder)
        return progressHolder.progress.coerceIn(0, 100)
    }

    /** [onDone] gets a human-readable location of the saved file. */
    fun export(
        sources: List<Source>,
        segments: List<Segment>,
        music: MusicTrack?,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (segments.isEmpty() || transformer != null) {
            onError("Nothing to export")
            return
        }

        // Render to the app's own folder first (Transformer writes to a file path)
        val tempDir = context.getExternalFilesDir(null) ?: context.filesDir
        tempDir.mkdirs()
        val tempFile = File(tempDir, "render_${System.currentTimeMillis()}.mp4")

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
                    // Copy to Movies/ZEVN off the main thread, then report back on the main thread
                    Thread {
                        try {
                            val location = saveToMovies(tempFile)
                            tempFile.delete()
                            mainHandler.post { onDone(location) }
                        } catch (e: Exception) {
                            mainHandler.post { onError("Rendered, but saving failed: ${e.message}") }
                        }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer = null
                    tempFile.delete()
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()

        transformer = t
        t.start(composition, tempFile.absolutePath)
    }

    /** Copies the rendered file into Movies/ZEVN and returns where it is. */
    private fun saveToMovies(source: File): String {
        val name = "ZEVN_${System.currentTimeMillis()}.mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ZEVN")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Cannot open output")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Movies/ZEVN/$name"
        } else {
            // Android 9 and older: public Movies folder (needs storage permission on these versions)
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ZEVN")
            dir.mkdirs()
            val dest = File(dir, name)
            source.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
