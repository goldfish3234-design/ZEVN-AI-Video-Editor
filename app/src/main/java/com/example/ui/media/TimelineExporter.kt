package com.example.ui.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Project settings that the export must apply to the video. */
data class ExportOptions(
    val aspectRatio: Float,
    val rotationDeg: Float,
    val flipH: Boolean,
    val flipV: Boolean
)

/**
 * Renders the timeline to an MP4 file (and copies it to Movies/ZEVN), and extracts audio from a clip.
 * Must be started from the main thread.
 */
class TimelineExporter(private val context: Context) {

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var silenceFile: File? = null

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
        options: ExportOptions,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (segments.isEmpty() || transformer != null) {
            onError("Nothing to export")
            return
        }

        val tempDir = context.getExternalFilesDir(null) ?: context.filesDir
        tempDir.mkdirs()
        val tempFile = File(tempDir, "render_${System.currentTimeMillis()}.mp4")
        val totalMs = segments.sumOf { it.durationMs }

        // ---- Video sequence: each clip keeps its own sound level ----
        val videoEffects = buildVideoEffects(options)
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
            EditedMediaItem.Builder(clipped)
                .setEffects(Effects(listOf(VolumeAudioProcessor(seg.volume)), videoEffects))
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).build())

        // ---- Music sequence: silence until the music offset, then the trimmed music ----
        if (music != null) {
            val musicLen = min(music.lengthMs, totalMs - music.offsetMs)
            if (musicLen > 0L) {
                val musicItems = mutableListOf<EditedMediaItem>()
                if (music.offsetMs > 0L) {
                    val silent = writeSilentWav(tempDir, music.offsetMs)
                    musicItems.add(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silent))).build())
                }
                val clipped = MediaItem.Builder()
                    .setUri(music.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(music.inMs)
                            .setEndPositionMs(music.inMs + musicLen)
                            .build()
                    )
                    .build()
                musicItems.add(
                    EditedMediaItem.Builder(clipped)
                        .setEffects(Effects(listOf(VolumeAudioProcessor(music.volume)), emptyList()))
                        .build()
                )
                sequences.add(EditedMediaItemSequence.Builder(musicItems).build())
            }
        }

        val composition = Composition.Builder(sequences).build()

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    Thread {
                        try {
                            val location = saveToMovies(tempFile)
                            tempFile.delete()
                            silenceFile?.delete()
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
                    silenceFile?.delete()
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()

        transformer = t
        t.start(composition, tempFile.absolutePath)
    }

    /**
     * Extracts only the sound of one clip into an audio file in the project folder.
     * [onDone] receives the audio file's Uri and its length in ms.
     */
    fun extractAudio(
        sources: List<Source>,
        seg: Segment,
        onDone: (Uri, Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (transformer != null) {
            onError("Another job is running")
            return
        }
        val outFile = File(ProjectStore.currentMediaDir(context), "audio_${System.nanoTime()}.m4a")
        val clipped = MediaItem.Builder()
            .setUri(sources[seg.source].uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(seg.startMs)
                    .setEndPositionMs(seg.endMs)
                    .build()
            )
            .build()
        val item = EditedMediaItem.Builder(clipped).setRemoveVideo(true).build()
        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.Builder(listOf(item)).build())
        ).build()

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    mainHandler.post { onDone(Uri.fromFile(outFile), seg.durationMs) }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer = null
                    outFile.delete()
                    mainHandler.post { onError(exportException.message ?: "Audio extract failed") }
                }
            })
            .build()

        transformer = t
        t.start(composition, outFile.absolutePath)
    }

    /** Rotation, flip and aspect ratio applied to every video frame. */
    private fun buildVideoEffects(options: ExportOptions): List<Effect> {
        val list = mutableListOf<Effect>()
        if (options.rotationDeg != 0f || options.flipH || options.flipV) {
            list.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(options.rotationDeg)
                    .setScale(if (options.flipH) -1f else 1f, if (options.flipV) -1f else 1f)
                    .build()
            )
        }
        list.add(Presentation.createForAspectRatio(options.aspectRatio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        return list
    }

    /** Writes a silent stereo 16-bit 44.1 kHz WAV file of the given length. */
    private fun writeSilentWav(dir: File, durationMs: Long): File {
        val sampleRate = 44100
        val channels = 2
        val bytesPerFrame = channels * 2
        val frames = (durationMs * sampleRate / 1000L).toInt()
        val dataSize = frames * bytesPerFrame
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * bytesPerFrame)
        buf.putShort(bytesPerFrame.toShort())
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        val file = File(dir, "silence_${System.currentTimeMillis()}.wav")
        file.writeBytes(buf.array())
        silenceFile = file
        return file
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
