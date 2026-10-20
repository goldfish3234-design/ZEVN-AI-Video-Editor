package com.example.ui.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.ui.sticker.StickerClip
import com.example.ui.sticker.StickerTrack
import com.example.ui.text.TextAnimMath
import com.example.ui.text.TextClip
import com.example.ui.text.TextPreviewMetrics
import com.example.ui.text.TextRenderer
import com.example.ui.text.TextTrack
import com.google.common.collect.ImmutableList
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/** Project settings that the export must apply to the video. */
data class ExportOptions(
    val aspectRatio: Float,
    val rotationDeg: Float,
    val flipH: Boolean,
    val flipV: Boolean
)

/** A part of one video segment that has the same look for all its overlays. */
private data class VideoPiece(
    val seg: Segment,
    val srcStart: Long,
    val srcEnd: Long,
    val tlStart: Long,
    val tlEnd: Long
)

/**
 * Renders the timeline to an MP4 file (and copies it to Movies/ZEVN), and extracts audio from a clip.
 * Clip speed and volume, text overlays (colour, outline, background, font, size, position, timing and
 * animation) and sticker/image overlays are all burned into the video. Overlapping audio clips are placed
 * in separate sequences, so Media3 mixes them. Must be started from the main thread.
 */
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

    /**
     * Cuts every video segment wherever a text or sticker starts or ends, and wherever a text's animation
     * changes, so that inside one piece every overlay has a fixed look.
     * Source positions are converted with each clip's speed.
     */
    private fun buildPieces(
        segments: List<Segment>,
        texts: List<TextClip>,
        stickers: List<StickerClip>,
        totalMs: Long
    ): List<VideoPiece> {
        val raw = mutableListOf(0L, totalMs)
        for (t in texts) {
            raw.add(t.startMs)
            raw.add(t.startMs + t.durationMs)
            raw.addAll(TextAnimMath.breakpoints(t))
        }
        for (s in stickers) {
            raw.add(s.startMs)
            raw.add(s.startMs + s.durationMs)
        }
        val breaks = raw.filter { it in 0L..totalMs }.distinct().sorted()

        val pieces = mutableListOf<VideoPiece>()
        var tl0 = 0L
        for (seg in segments) {
            val tl1 = tl0 + seg.durationMs
            val points = listOf(tl0) + breaks.filter { it > tl0 && it < tl1 } + listOf(tl1)
            for (k in 0 until points.size - 1) {
                val a = points[k]
                val b = points[k + 1]
                if (b > a) {
                    val srcA = seg.startMs + ((a - tl0) * seg.speed).toLong()
                    val srcB = seg.startMs + ((b - tl0) * seg.speed).toLong()
                    pieces.add(VideoPiece(seg, srcA, srcB, a, b))
                }
            }
            tl0 = tl1
        }
        return pieces
    }

    /**
     * The size of the exported frame, worked out from the first clip's real resolution and the
     * chosen aspect ratio (the same crop the export uses). Text is drawn at this size, so it matches the preview.
     */
    private fun frameSize(sources: List<Source>, aspectRatio: Float): Pair<Float, Float> {
        var srcW = 1080f
        var srcH = 1920f
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sources.first().uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w != null && h != null && w > 0f && h > 0f) {
                if (rot == 90 || rot == 270) {
                    srcW = h
                    srcH = w
                } else {
                    srcW = w
                    srcH = h
                }
            }
        } catch (e: Exception) {
            // keep the default size
        } finally {
            retriever.release()
        }
        return if (aspectRatio < 1f) {
            val frameH = min(srcH, srcW / aspectRatio)
            Pair(frameH * aspectRatio, frameH)
        } else {
            val frameW = min(srcW, srcH * aspectRatio)
            Pair(frameW, frameW / aspectRatio)
        }
    }

    /** Audio processors for one clip: speed (keeps the pitch) and volume. A new instance is needed per item. */
    private fun audioProcessorsFor(speed: Float, volume: Float): List<AudioProcessor> {
        val list = mutableListOf<AudioProcessor>()
        if (abs(speed - 1f) > 0.001f) {
            val sonic = SonicAudioProcessor()
            sonic.setSpeed(speed)
            sonic.setPitch(1f)
            list.add(sonic)
        }
        list.add(VolumeAudioProcessor(volume))
        return list
    }

    /** [onDone] gets a human-readable location of the saved file. */
    @OptIn(UnstableApi::class)
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

        // ---- Video sequence: cut at overlay boundaries; each clip keeps its speed and sound level ----
        val texts = TextTrack.texts.value
        val stickers = StickerTrack.stickers.value
        val previewWidthDp = TextPreviewMetrics.widthDp
        val (frameW, frameH) = frameSize(sources, options.aspectRatio)
        val bitmapCache = HashMap<String, Bitmap>()
        val videoItems = buildPieces(segments, texts, stickers, totalMs).map { p ->
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
            val effects = mutableListOf<Effect>()
            if (abs(p.seg.speed - 1f) > 0.001f) {
                effects.add(SpeedChangeEffect(p.seg.speed))
            }
            effects.addAll(buildVideoEffects(options))

            val overlays = mutableListOf<TextureOverlay>()
            for (t in texts) {
                val st = TextAnimMath.stateAt(t, mid) ?: continue
                val overlay = overlayFor(t, st, frameW, frameH, previewWidthDp, bitmapCache)
                if (overlay != null) overlays.add(overlay)
            }
            for (s in stickers) {
                if (mid < s.startMs || mid >= s.startMs + s.durationMs) continue
                val overlay = stickerOverlay(s, frameW, frameH, bitmapCache)
                if (overlay != null) overlays.add(overlay)
            }
            if (overlays.isNotEmpty()) {
                effects.add(OverlayEffect(ImmutableList.copyOf(overlays)))
            }

            EditedMediaItem.Builder(clipped)
                .setEffects(Effects(audioProcessorsFor(p.seg.speed, p.seg.volume), effects))
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
                        .setEffects(Effects(audioProcessorsFor(1f, c.volume), emptyList()))
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
     * One text overlay at one moment, placed where it appears in the preview.
     * [frameW] x [frameH] is the exported frame in pixels. The preview width [previewWidthDp] is
     * mapped onto the frame width, so sizes are the same in the preview and in the export.
     * Returns null when the text is invisible.
     */
    @OptIn(UnstableApi::class)
    private fun overlayFor(
        t: TextClip,
        st: TextAnimMath.State,
        frameW: Float,
        frameH: Float,
        previewWidthDp: Float,
        cache: HashMap<String, Bitmap>
    ): BitmapOverlay? {
        if (st.alpha < 0.01f) return null
        val pxPerDp = frameW / (if (previewWidthDp > 0f) previewWidthDp else 360f)

        val base = cache.getOrPut("${t.id}|100") { TextRenderer.render(context, t, pxPerDp) }
        val sized = if (abs(st.scale - 1f) < 0.01f) {
            base
        } else {
            val key = "${t.id}|${(st.scale * 100).toInt()}"
            cache.getOrPut(key) {
                TextRenderer.render(context, t.copy(sizeSp = t.sizeSp * st.scale), pxPerDp)
            }
        }

        // the text is anchored by its top-left corner in the preview; the overlay is anchored by its centre
        val cx = t.xFrac + (base.width / frameW) / 2f
        val cy = t.yFrac + (base.height / frameH) / 2f + (st.dyDp * pxPerDp) / frameH
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(cx * 2f - 1f, 1f - cy * 2f)
            .setAlphaScale(st.alpha)
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(sized, settings)
    }

    /**
     * One sticker/image overlay. The picture is scaled to [StickerClip.sizeFrac] of the frame width and
     * placed at the same position as in the preview (top-left corner at xFrac, yFrac).
     */
    @OptIn(UnstableApi::class)
    private fun stickerOverlay(
        s: StickerClip,
        frameW: Float,
        frameH: Float,
        cache: HashMap<String, Bitmap>
    ): BitmapOverlay? {
        val key = "s${s.id}"
        val bmp = cache[key] ?: loadSticker(s, frameW)?.also { cache[key] = it } ?: return null
        val cx = s.xFrac + (bmp.width / frameW) / 2f
        val cy = s.yFrac + (bmp.height / frameH) / 2f
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(cx * 2f - 1f, 1f - cy * 2f)
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(bmp, settings)
    }

    /** Loads a sticker picture and scales it to the export size. Returns null if the file cannot be read. */
    private fun loadSticker(s: StickerClip, frameW: Float): Bitmap? {
        val path = Uri.parse(s.uri).path ?: return null
        val decoded = BitmapFactory.decodeFile(path) ?: return null
        val targetW = (frameW * s.sizeFrac).toInt().coerceAtLeast(1)
        val targetH = (decoded.height.toFloat() * targetW / decoded.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
    }

    /**
     * Extracts only the sound of one clip into an audio file in the project folder.
     * The sound keeps the clip's speed. [onDone] receives the audio file's Uri and its length in ms.
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
        val item = EditedMediaItem.Builder(clipped)
            .setRemoveVideo(true)
            .setEffects(Effects(audioProcessorsFor(seg.speed, 1f), emptyList()))
            .build()
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
