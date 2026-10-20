package com.example.ui.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.ui.text.TextClip
import com.example.ui.text.TextRenderer
import com.example.ui.text.TextTrack
import com.google.common.collect.ImmutableList
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

/** A part of one video segment that has the same set of visible texts. */
private data class VideoPiece(
    val seg: Segment,
    val srcStart: Long,
    val srcEnd: Long,
    val tlStart: Long,
    val tlEnd: Long
)

/**
 * Renders the timeline to an MP4 file (and copies it to Movies/ZEVN), and extracts audio from a clip.
 * Text overlays from the text track are burned into the video. Overlapping audio clips are placed in
 * separate sequences, so Media3 mixes them together. Must be started from the main thread.
 */
@UnstableApi
class TimelineExporter(private val context: Context) {

    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val silenceFiles = mutableListOf<File>()

    /** Progress 0..100 while exporting. */
    fun progress(): Int {
        val t = transformer ?: return 0
        t.getProgress(progressHolder)
        return progressHolder.progress.coerceIn(0, 100)
    }

    /** Splits audio clips into lanes so that clips in one lane never overlap. */
    private fun audioLanesOf(clips: List<MusicTrack>): List<List<MusicTrack>> {
        val order = clips.sortedBy { it.offsetMs }
        val laneEnds = ArrayList<Long>()
        val lanes = ArrayList<MutableList<MusicTrack>>()
        for (c in order) {
            val start = c.offsetMs
            val end = start + c.lengthMs
            var placed = false
            for (l in laneEnds.indices) {
                if (laneEnds[l] <= start) {
                    laneEnds[l] = end
                    lanes[l].add(c)
                    placed = true
                    break
                }
            }
            if (!placed) {
                laneEnds.add(end)
                lanes.add(mutableListOf(c))
            }
        }
        return lanes
    }

    /** Cuts every video segment wherever a text starts or ends, so each piece has a fixed set of texts. */
    private fun buildPieces(segments: List<Segment>, texts: List<TextClip>, totalMs: Long): List<VideoPiece> {
        val breaks = (listOf(0L, totalMs) + texts.flatMap { listOf(it.startMs, it.startMs + it.durationMs) })
            .filter { it in 0L..totalMs }
            .distinct()
            .sorted()
        val pieces = mutableListOf<VideoPiece>()
        var tl0 = 0L
        for (seg in segments) {
            val tl1 = tl0 + seg.durationMs
            val points = listOf(tl0) + breaks.filter { it > tl0 && it < tl1 } + listOf(tl1)
            for (k in 0 until points.size - 1) {
                val a = points[k]
                val b = points[k + 1]
                if (b > a) {
                    pieces.add(VideoPiece(seg, seg.startMs + (a - tl0), seg.startMs + (b - tl0), a, b))
                }
            }
            tl0 = tl1
        }
        return pieces
    }

    /** [onDone] gets a human-readable location of the saved file. */
    fun export(
        sources: List<Source>,
        segments: List<Segment>,
        music: List<MusicTrack>,
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

        // ---- Video sequence: cut at text boundaries, each clip keeps its own sound level ----
        val texts = TextTrack.texts.value
        val videoEffects = buildVideoEffects(options)
        val bitmapCache = HashMap<Long, Bitmap>()
        val videoItems = buildPieces(segments, texts, totalMs).map { p ->
            val clipped = MediaItem.Builder()
                .setUri(sources[p.seg.source].uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(p.srcStart)
                        .setEndPositionMs(p.srcEnd)
                        .build()
                )
                .build()
            val mid = (p.tlStart + p.tlEnd) / 2
            val active = texts.filter { mid >= it.startMs && mid < it.startMs + it.durationMs }
            val effects = mutableListOf<Effect>()
            effects.addAll(videoEffects)
            if (active.isNotEmpty()) {
                val overlays: List<TextureOverlay> = active.map { overlayFor(it, options.aspectRatio, bitmapCache) }
                effects.add(OverlayEffect(ImmutableList.copyOf(overlays)))
            }
            EditedMediaItem.Builder(clipped)
                .setEffects(Effects(listOf(VolumeAudioProcessor(p.seg.volume)), effects))
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).build())

        // ---- Audio sequences: one per lane, clips in order with silence in the gaps ----
        for (lane in audioLanesOf(music)) {
            val laneItems = mutableListOf<EditedMediaItem>()
            var cursor = 0L
            for (c in lane) {
                val len = min(c.lengthMs, totalMs - c.offsetMs)
                if (len <= 0L) continue
                if (c.offsetMs > cursor) {
                    val silent = writeSilentWav(tempDir, c.offsetMs - cursor)
                    laneItems.add(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silent))).build())
                }
                val clipped = MediaItem.Builder()
                    .setUri(c.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.inMs)
                            .setEndPositionMs(c.inMs + len)
                            .build()
                    )
                    .build()
                laneItems.add(
                    EditedMediaItem.Builder(clipped)
                        .setEffects(Effects(listOf(VolumeAudioProcessor(c.volume)), emptyList()))
                        .build()
                )
                cursor = c.offsetMs + len
            }
            if (laneItems.isNotEmpty()) {
                sequences.add(EditedMediaItemSequence.Builder(laneItems).build())
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
                            deleteSilence()
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
                    deleteSilence()
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()

        transformer = t
        t.start(composition, tempFile.absolutePath)
    }

    /**
     * A text overlay placed where it appears in the preview.
     * The export frame is 1080 px wide; the preview is taken as about 360 dp wide.
     */
    private fun overlayFor(
        t: TextClip,
        aspectRatio: Float,
        cache: HashMap<Long, Bitmap>
    ): BitmapOverlay {
        val frameW = 1080f
        val frameH = frameW / aspectRatio
        val scale = frameW / 360f
        val bmp = cache.getOrPut(t.id) { TextRenderer.render(context, t, scale) }
        // the bitmap's top-left sits at (xFrac, yFrac) of the frame; the overlay is anchored by its centre
        val cx = t.xFrac + (bmp.width / frameW) / 2f
        val cy = t.yFrac + (bmp.height / frameH) / 2f
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(cx * 2f - 1f, 1f - cy * 2f)
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(bmp, settings)
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
        val file = File(dir, "silence_${System.nanoTime()}.wav")
        file.writeBytes(buf.array())
        silenceFiles.add(file)
        return file
    }

    private fun deleteSilence() {
        silenceFiles.forEach { it.delete() }
        silenceFiles.clear()
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
        deleteSilence()
    }
}
