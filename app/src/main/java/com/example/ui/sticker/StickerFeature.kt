package com.example.ui.sticker

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.media.ProjectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val MIN_STICKER_MS = 300L
private const val EDGE_PX = 48f

/** Kinds of picture layer. */
object LayerMode {
    /** A small sticker that can be moved and resized. */
    const val STICKER = "sticker"
    /** A photo that fills the whole frame for its time on the timeline (like a video clip). */
    const val PHOTO = "photo"
    /** A small picture in a corner over the video (picture-in-picture). */
    const val PIP = "pip"
}

/**
 * A picture layer on the timeline.
 * Position is a fraction of the preview (0..1). [sizeFrac] is the width as a fraction of the preview width.
 * [uri] is a file:// address of a picture. If [frames] is not empty, this is an animated picture: the frames
 * play one after another at [fps]. [mode] is one of [LayerMode].
 */
data class StickerClip(
    val id: Long,
    val uri: String,
    val startMs: Long,
    val durationMs: Long,
    val xFrac: Float,
    val yFrac: Float,
    val sizeFrac: Float,
    val frames: List<String> = emptyList(),
    val fps: Float = 10f,
    val mode: String = LayerMode.STICKER
)

/** The picture track: the list of layers and which one is selected. */
object StickerTrack {
    private val _stickers = MutableStateFlow<List<StickerClip>>(emptyList())
    val stickers: StateFlow<List<StickerClip>> = _stickers.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selectedSticker: StateFlow<Int> = _selected.asStateFlow()

    private var nextId = 1L

    fun restore(list: List<StickerClip>) {
        _stickers.value = list
        nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        _selected.value = -1
    }

    fun newId(): Long = nextId++

    /** Where a new layer of this [mode] appears on the preview. */
    private fun placement(mode: String): Triple<Float, Float, Float> = when (mode) {
        LayerMode.PHOTO -> Triple(0f, 0f, 1f)
        LayerMode.PIP -> Triple(0.6f, 0.6f, 0.36f)
        else -> Triple(0.3f, 0.3f, 0.3f)
    }

    /** Adds a picture (sticker, photo or picture-in-picture) at [startMs] and selects it. */
    fun add(uri: String, startMs: Long, durationMs: Long, mode: String = LayerMode.STICKER) {
        val (x, y, size) = placement(mode)
        val clip = StickerClip(
            id = newId(),
            uri = uri,
            startMs = startMs.coerceAtLeast(0L),
            durationMs = durationMs,
            xFrac = x,
            yFrac = y,
            sizeFrac = size,
            mode = mode
        )
        _stickers.value = _stickers.value + clip
        _selected.value = _stickers.value.lastIndex
    }

    /** Adds an animated picture (a list of frames) at [startMs] and selects it. */
    fun addAnimated(frames: List<String>, fps: Float, startMs: Long, mode: String = LayerMode.STICKER) {
        if (frames.isEmpty()) return
        val (x, y, size) = placement(mode)
        val clip = StickerClip(
            id = newId(),
            uri = frames.first(),
            startMs = startMs.coerceAtLeast(0L),
            durationMs = (frames.size * 1000f / fps).toLong(),
            xFrac = x,
            yFrac = y,
            sizeFrac = if (mode == LayerMode.STICKER) 0.4f else size,
            frames = frames,
            fps = fps,
            mode = mode
        )
        _stickers.value = _stickers.value + clip
        _selected.value = _stickers.value.lastIndex
    }

    /** The picture to show for [s] at timeline time [timeMs]. Animated pictures loop. */
    fun frameUriAt(s: StickerClip, timeMs: Long): String {
        if (s.frames.isEmpty()) return s.uri
        val elapsed = (timeMs - s.startMs).coerceAtLeast(0L)
        val index = ((elapsed * s.fps) / 1000f).toInt() % s.frames.size
        return s.frames[index]
    }

    fun replace(index: Int, clip: StickerClip) {
        val list = _stickers.value
        if (index !in list.indices) return
        val out = ArrayList(list)
        out[index] = clip
        _stickers.value = out
    }

    fun delete(index: Int) {
        val list = _stickers.value
        if (index !in list.indices) return
        val out = ArrayList(list)
        out.removeAt(index)
        _stickers.value = out
        _selected.value = -1
    }

    fun select(index: Int) {
        _selected.value = if (index in _stickers.value.indices) index else -1
    }

    /** Moves the layer on the preview. Values are fractions of the preview. A photo always fills the frame. */
    fun setPosition(index: Int, x: Float, y: Float) {
        val c = _stickers.value.getOrNull(index) ?: return
        if (c.mode == LayerMode.PHOTO) return
        replace(index, c.copy(xFrac = x.coerceIn(-0.2f, 1f), yFrac = y.coerceIn(-0.2f, 1f)))
    }

    fun setSize(index: Int, size: Float) {
        val c = _stickers.value.getOrNull(index) ?: return
        if (c.mode == LayerMode.PHOTO) return
        replace(index, c.copy(sizeFrac = size.coerceIn(0.05f, 1f)))
    }

    fun moveBy(index: Int, deltaMs: Long, totalMs: Long) {
        val c = _stickers.value.getOrNull(index) ?: return
        val maxStart = maxOf(0L, totalMs - c.durationMs)
        replace(index, c.copy(startMs = (c.startMs + deltaMs).coerceIn(0L, maxStart)))
    }

    fun trimStartBy(index: Int, deltaMs: Long) {
        val c = _stickers.value.getOrNull(index) ?: return
        val end = c.startMs + c.durationMs
        val newStart = (c.startMs + deltaMs).coerceIn(0L, end - MIN_STICKER_MS)
        replace(index, c.copy(startMs = newStart, durationMs = end - newStart))
    }

    fun trimEndBy(index: Int, deltaMs: Long, totalMs: Long) {
        val c = _stickers.value.getOrNull(index) ?: return
        val maxDur = if (totalMs > 0L) maxOf(MIN_STICKER_MS, totalMs - c.startMs) else Long.MAX_VALUE
        replace(index, c.copy(durationMs = (c.durationMs + deltaMs).coerceIn(MIN_STICKER_MS, maxDur)))
    }

    /** Moves the start of the layer to the playhead (keeps its end). */
    fun trimStartToPlayhead(index: Int, playheadMs: Long) {
        val c = _stickers.value.getOrNull(index) ?: return
        val end = c.startMs + c.durationMs
        if (playheadMs < 0L || playheadMs > end - MIN_STICKER_MS) return
        replace(index, c.copy(startMs = playheadMs, durationMs = end - playheadMs))
    }

    /** Moves the end of the layer to the playhead (keeps its start). */
    fun trimEndToPlayhead(index: Int, playheadMs: Long) {
        val c = _stickers.value.getOrNull(index) ?: return
        if (playheadMs < c.startMs + MIN_STICKER_MS) return
        replace(index, c.copy(durationMs = playheadMs - c.startMs))
    }
}

/** Saves and loads the picture layers of the current project (stickers.json in the project folder). */
object StickerStore {

    fun save(context: Context, list: List<StickerClip>) {
        val arr = JSONArray()
        for (s in list) {
            val framesArr = JSONArray()
            for (f in s.frames) framesArr.put(f)
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("uri", s.uri)
                    .put("startMs", s.startMs)
                    .put("durationMs", s.durationMs)
                    .put("xFrac", s.xFrac.toDouble())
                    .put("yFrac", s.yFrac.toDouble())
                    .put("sizeFrac", s.sizeFrac.toDouble())
                    .put("frames", framesArr)
                    .put("fps", s.fps.toDouble())
                    .put("mode", s.mode)
            )
        }
        File(ProjectStore.currentMediaDir(context), "stickers.json").writeText(arr.toString())
    }

    fun load(context: Context): List<StickerClip> {
        val f = File(ProjectStore.currentMediaDir(context), "stickers.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<StickerClip>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val framesArr = o.optJSONArray("frames")
                val frames = mutableListOf<String>()
                if (framesArr != null) {
                    for (k in 0 until framesArr.length()) frames.add(framesArr.getString(k))
                }
                out.add(
                    StickerClip(
                        id = o.getLong("id"),
                        uri = o.getString("uri"),
                        startMs = o.getLong("startMs"),
                        durationMs = o.getLong("durationMs"),
                        xFrac = o.optDouble("xFrac", 0.3).toFloat(),
                        yFrac = o.optDouble("yFrac", 0.3).toFloat(),
                        sizeFrac = o.optDouble("sizeFrac", 0.3).toFloat(),
                        frames = frames,
                        fps = o.optDouble("fps", 10.0).toFloat(),
                        mode = o.optString("mode", LayerMode.STICKER)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Draws the picture layers that are visible at the playhead.
 * A photo fills the frame; a sticker or picture-in-picture can be dragged; tap to select.
 */
@Composable
fun StickerLayer(
    stickers: List<StickerClip>,
    playheadMs: Long,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        stickers.forEachIndexed { i, s ->
            if (playheadMs >= s.startMs && playheadMs < s.startMs + s.durationMs) {
                if (s.mode == LayerMode.PHOTO) {
                    AsyncImage(
                        model = StickerTrack.frameUriAt(s, playheadMs),
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(i) {
                                detectTapGestures(onTap = { StickerTrack.select(i) })
                            }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .offset(x = w * s.xFrac, y = h * s.yFrac)
                            .width(w * s.sizeFrac)
                            .then(
                                if (i == selectedIndex) {
                                    Modifier.border(2.dp, Color(0xFFFFB800), RoundedCornerShape(4.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(i, wPx, hPx) {
                                detectTapGestures(onTap = { StickerTrack.select(i) })
                            }
                            .pointerInput(i, wPx, hPx) {
                                detectDragGestures(
                                    onDragStart = { StickerTrack.select(i) },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        if (wPx > 0f && hPx > 0f) {
                                            val cur = StickerTrack.stickers.value.getOrNull(i) ?: return@detectDragGestures
                                            StickerTrack.setPosition(i, cur.xFrac + drag.x / wPx, cur.yFrac + drag.y / hPx)
                                        }
                                    }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = StickerTrack.frameUriAt(s, playheadMs),
                            contentDescription = "Sticker",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Picture track: one block per layer, same edit gestures as the text track. */
@Composable
fun StickerTrackBar(
    stickers: List<StickerClip>,
    totalMs: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    BoxWithConstraints(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141C))
    ) {
        val trackWidth = maxWidth
        stickers.forEachIndexed { i, s ->
            val startFrac = (s.startMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val lenFrac = (s.durationMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val color = if (i == selectedIndex) Color(0xFF7CE38B) else Color(0xFF3E8E50)
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * startFrac)
                    .width(maxOf(trackWidth * lenFrac, 6.dp))
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .pointerInput(i, total) {
                        detectTapGestures(onTap = { onSelect(i) })
                    }
                    .pointerInput(i, total) {
                        // 0 = move, 1 = trim start, 2 = trim end
                        var mode = 0
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat()
                                mode = when {
                                    offset.x < EDGE_PX -> 1
                                    offset.x > w - EDGE_PX -> 2
                                    else -> 0
                                }
                                onSelect(i)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (size.width > 0) {
                                    val deltaMs = (drag.x / size.width.toFloat() * total.toFloat()).toLong()
                                    when (mode) {
                                        0 -> StickerTrack.moveBy(i, deltaMs, total)
                                        1 -> StickerTrack.trimStartBy(i, deltaMs)
                                        else -> StickerTrack.trimEndBy(i, deltaMs, total)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = when (s.mode) {
                        LayerMode.PHOTO -> "Photo ${i + 1}"
                        LayerMode.PIP -> "PiP ${i + 1}"
                        else -> if (s.frames.isNotEmpty()) "Cut-out ${i + 1}" else "Sticker ${i + 1}"
                    },
                    fontSize = 9.sp,
                    color = Color.Black,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}
