package com.example.ui.text

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.media.TimelineModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val HANDLE_PX = 48f
private const val MIN_TEXT_MS = 300L

/**
 * One text overlay. Times are timeline ms. Position is a fraction of the preview (0..1).
 * [outlineWidth] is in dp (0 = no outline). [bgColor] is ARGB, used when [background] is on.
 * [anim] is one of: none, fade, slide, pop, drop.
 */
data class TextClip(
    val id: Long,
    val text: String,
    val startMs: Long,
    val durationMs: Long,
    val fontId: String,
    val sizeSp: Float,
    val colorArgb: Long,
    val bold: Boolean,
    val background: Boolean,
    val xFrac: Float,
    val yFrac: Float,
    val outlineColor: Long = 0xFF000000L,
    val outlineWidth: Float = 0f,
    val bgColor: Long = 0xCC000000L,
    val anim: String = "none"
)

/** Built-in fonts plus fonts the user imported from the phone (stored in app storage). */
object FontLibrary {
    private val builtIn = listOf(
        "default" to "Default",
        "serif" to "Serif",
        "sans" to "Sans",
        "mono" to "Mono",
        "cursive" to "Handwriting"
    )

    private fun fontDir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    /** (id, label) for every font the user can pick. */
    fun options(context: Context): List<Pair<String, String>> {
        val imported = fontDir(context).listFiles().orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name }
            .map { "file:${it.name}" to it.nameWithoutExtension }
        return builtIn + imported
    }

    fun family(context: Context, id: String): FontFamily = when {
        id.startsWith("file:") -> {
            val f = File(fontDir(context), id.removePrefix("file:"))
            if (f.exists()) {
                try {
                    FontFamily(Font(f))
                } catch (e: Exception) {
                    FontFamily.Default
                }
            } else {
                FontFamily.Default
            }
        }
        id == "serif" -> FontFamily.Serif
        id == "sans" -> FontFamily.SansSerif
        id == "mono" -> FontFamily.Monospace
        id == "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    /** Copies a picked font file into app storage and returns its id. Call from a background thread. */
    fun import(context: Context, uri: Uri): String {
        val name = "font_${System.currentTimeMillis()}.ttf"
        val dest = File(fontDir(context), name)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read the font file")
        input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
        return "file:$name"
    }
}

/** The text track: the list of text overlays and which one is selected. */
object TextTrack {
    private val _texts = MutableStateFlow<List<TextClip>>(emptyList())
    val texts: StateFlow<List<TextClip>> = _texts.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selectedText: StateFlow<Int> = _selected.asStateFlow()

    private var nextId = 1L

    fun restore(list: List<TextClip>) {
        _texts.value = list
        nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        _selected.value = -1
    }

    fun newId(): Long = nextId++

    fun add(clip: TextClip) {
        _texts.value = _texts.value + clip
        _selected.value = _texts.value.lastIndex
    }

    fun replace(index: Int, clip: TextClip) {
        val list = _texts.value
        if (index !in list.indices) return
        val out = ArrayList(list)
        out[index] = clip
        _texts.value = out
    }

    fun delete(index: Int) {
        val list = _texts.value
        if (index !in list.indices) return
        val out = ArrayList(list)
        out.removeAt(index)
        _texts.value = out
        _selected.value = -1
    }

    fun duplicate(index: Int) {
        val c = _texts.value.getOrNull(index) ?: return
        add(c.copy(id = newId(), startMs = c.startMs + c.durationMs))
    }

    fun select(index: Int) {
        _selected.value = if (index in _texts.value.indices) index else -1
    }

    fun moveBy(index: Int, deltaMs: Long, totalMs: Long) {
        val c = _texts.value.getOrNull(index) ?: return
        val maxStart = maxOf(0L, totalMs - c.durationMs)
        replace(index, c.copy(startMs = (c.startMs + deltaMs).coerceIn(0L, maxStart)))
    }

    /** Moves the start edge: the text starts later or earlier, and its end stays where it is. */
    fun trimStartBy(index: Int, deltaMs: Long) {
        val c = _texts.value.getOrNull(index) ?: return
        val end = c.startMs + c.durationMs
        val newStart = (c.startMs + deltaMs).coerceIn(0L, end - MIN_TEXT_MS)
        replace(index, c.copy(startMs = newStart, durationMs = end - newStart))
    }

    /** Moves the end edge: the text gets longer or shorter. */
    fun trimEndBy(index: Int, deltaMs: Long, totalMs: Long) {
        val c = _texts.value.getOrNull(index) ?: return
        val maxDur = if (totalMs > 0L) maxOf(MIN_TEXT_MS, totalMs - c.startMs) else Long.MAX_VALUE
        replace(index, c.copy(durationMs = (c.durationMs + deltaMs).coerceIn(MIN_TEXT_MS, maxDur)))
    }

    /** Cuts the text with this id into two texts at [timeMs] (timeline ms). Does nothing if the cut is too close to an edge. */
    fun splitById(id: Long, timeMs: Long) {
        val index = _texts.value.indexOfFirst { it.id == id }
        if (index < 0) return
        val c = _texts.value[index]
        val end = c.startMs + c.durationMs
        if (timeMs - c.startMs < MIN_TEXT_MS || end - timeMs < MIN_TEXT_MS) return
        val left = c.copy(durationMs = timeMs - c.startMs)
        val right = c.copy(id = newId(), startMs = timeMs, durationMs = end - timeMs)
        val out = ArrayList(_texts.value)
        out[index] = left
        out.add(index + 1, right)
        _texts.value = out
        _selected.value = index + 1
    }
}

/** Text colour choices (used for the text and the outline). */
val TextColors: List<Long> = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFFFD60AL, 0xFFFF5238L, 0xFF00E5FFL, 0xFF00FF9DL, 0xFFFF9F0AL,
    0xFFB388FFL, 0xFF2979FFL
)

/** Background colour choices, with transparency built in. */
val BackgroundColors: List<Long> = listOf(
    0xCC000000L, 0x66000000L, 0xCCFFFFFFL, 0xCCFF5238L, 0xCCFFD60AL,
    0xCC00E5FFL, 0xCC1E88E5L, 0xCC7C4DFFL, 0xCC00C853L
)

/** Animation choices: (key, label). */
val TextAnimations: List<Pair<String, String>> = listOf(
    "none" to "None",
    "fade" to "Fade",
    "slide" to "Slide up",
    "pop" to "Pop",
    "drop" to "Drop"
)

/** Draws the text overlays that are visible at the playhead, on top of the video preview. */
@Composable
fun TextOverlayLayer(texts: List<TextClip>, playheadMs: Long, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        // remember the preview width so the export can size the text the same way
        TextPreviewMetrics.widthDp = w.value
        texts.forEach { t ->
            if (playheadMs >= t.startMs && playheadMs < t.startMs + t.durationMs) {
                TextOverlayItem(t, w, h, playheadMs)
            }
        }
    }
}

@Composable
private fun TextOverlayItem(t: TextClip, w: Dp, h: Dp, playheadMs: Long) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val family = remember(t.fontId) { FontLibrary.family(context, t.fontId) }

    // Same animation math as the export
    val state = TextAnimMath.stateAt(t, playheadMs) ?: return

    val bg = if (t.background) {
        Modifier
            .background(Color(t.bgColor), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier
    }
    val outlinePx = with(density) { t.outlineWidth.dp.toPx() }
    val weight = if (t.bold) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .offset(x = w * t.xFrac, y = h * t.yFrac + state.dyDp.dp)
            .graphicsLayer {
                alpha = if (t.anim == "none") 1f else state.alpha
                scaleX = state.scale
                scaleY = state.scale
            }
            .then(bg)
    ) {
        if (t.outlineWidth > 0f) {
            Text(
                text = t.text,
                style = TextStyle(
                    fontFamily = family,
                    fontSize = t.sizeSp.sp,
                    fontWeight = weight,
                    textAlign = TextAlign.Start,
                    drawStyle = Stroke(width = outlinePx * 2f, join = StrokeJoin.Round)
                ),
                color = Color(t.outlineColor)
            )
        }
        Text(
            text = t.text,
            fontFamily = family,
            fontSize = t.sizeSp.sp,
            fontWeight = weight,
            color = Color(t.colorArgb),
            textAlign = TextAlign.Start
        )
    }
}

/**
 * Text track: one block per text overlay.
 * Tap to select, drag the middle to move in time, drag the left/right edge to change the start/end.
 */
@Composable
fun TextTrackView(
    texts: List<TextClip>,
    totalMs: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onMoveBy: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    BoxWithConstraints(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141C))
    ) {
        val trackWidth = maxWidth
        texts.forEachIndexed { i, t ->
            val startFrac = (t.startMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val lenFrac = (t.durationMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * startFrac)
                    .width(maxOf(trackWidth * lenFrac, 6.dp))
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (i == selectedIndex) Color(0xFFFFB800) else Color(0xFFD99A00))
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
                                    offset.x < HANDLE_PX -> 1
                                    offset.x > w - HANDLE_PX -> 2
                                    else -> 0
                                }
                                onSelect(i)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (size.width > 0) {
                                    val deltaMs = (drag.x / size.width.toFloat() * total.toFloat()).toLong()
                                    when (mode) {
                                        0 -> onMoveBy(i, deltaMs)
                                        1 -> TextTrack.trimStartBy(i, deltaMs)
                                        else -> TextTrack.trimEndBy(i, deltaMs, total)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = t.text,
                    fontSize = 10.sp,
                    color = Color.Black,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

/**
 * Colour plate: pick any colour with the red, green, blue (and optionally alpha) sliders.
 * Shows a live swatch and the hex code.
 */
@Composable
fun ColorPlate(color: Long, showAlpha: Boolean, onChange: (Long) -> Unit) {
    val a = ((color shr 24) and 0xFF).toInt()
    val r = ((color shr 16) and 0xFF).toInt()
    val g = ((color shr 8) and 0xFF).toInt()
    val b = (color and 0xFF).toInt()

    fun pack(na: Int, nr: Int, ng: Int, nb: Int): Long =
        ((na.toLong() and 0xFF) shl 24) or ((nr.toLong() and 0xFF) shl 16) or
            ((ng.toLong() and 0xFF) shl 8) or (nb.toLong() and 0xFF)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(color))
                    .border(1.dp, Color(0xFF555566), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "#" + java.lang.Long.toHexString(color).uppercase().padStart(8, '0'),
                color = Color.White,
                fontSize = 12.sp
            )
        }
        ChannelSlider("R", r, Color(0xFFFF5252)) { onChange(pack(a, it, g, b)) }
        ChannelSlider("G", g, Color(0xFF69F0AE)) { onChange(pack(a, r, it, b)) }
        ChannelSlider("B", b, Color(0xFF448AFF)) { onChange(pack(a, r, g, it)) }
        if (showAlpha) {
            ChannelSlider("A", a, Color(0xFFBDBDBD)) { onChange(pack(it, r, g, b)) }
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f)
        )
        Text("$value", color = Color(0xFFA0A0B2), fontSize = 11.sp, modifier = Modifier.width(34.dp))
    }
}

/**
 * Dialog to add or edit a text overlay:
 * text, font, size, colours (text / outline / background, with a colour plate), outline width,
 * style, position, duration and animation. Also "Cut at playhead" to split the text.
 */
@Composable
fun TextEditorDialog(
    initial: TextClip?,
    defaultStartMs: Long,
    onDismiss: () -> Unit,
    onSave: (TextClip) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(initial?.text ?: "Your text") }
    var fontId by remember { mutableStateOf(initial?.fontId ?: "default") }
    var sizeSp by remember { mutableStateOf(initial?.sizeSp ?: 28f) }
    var colorArgb by remember { mutableStateOf(initial?.colorArgb ?: 0xFFFFFFFFL) }
    var outlineColor by remember { mutableStateOf(initial?.outlineColor ?: 0xFF000000L) }
    var outlineWidth by remember { mutableStateOf(initial?.outlineWidth ?: 0f) }
    var bold by remember { mutableStateOf(initial?.bold ?: true) }
    var background by remember { mutableStateOf(initial?.background ?: false) }
    var bgColor by remember { mutableStateOf(initial?.bgColor ?: 0xCC000000L) }
    var durationMs by remember { mutableStateOf((initial?.durationMs ?: 3000L).toFloat()) }
    var yFrac by remember { mutableStateOf(initial?.yFrac ?: 0.8f) }
    var anim by remember { mutableStateOf(initial?.anim ?: "none") }
    var fonts by remember { mutableStateOf(FontLibrary.options(context)) }
    // Which colour the plate edits: 0 = text, 1 = outline, 2 = background
    var colorTarget by remember { mutableStateOf(0) }

    val importFont = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val id = withContext(Dispatchers.IO) { FontLibrary.import(context, uri) }
                    fonts = FontLibrary.options(context)
                    fontId = id
                } catch (e: Exception) {
                    // ignore: the file could not be read
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161622),
        title = { Text("Text", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5238)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                DialogTitle("Font")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    fonts.forEach { (id, label) ->
                        Chip(label, selected = fontId == id, color = Color(0xFFFF5238)) { fontId = id }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    importFont.launch(
                        arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")
                    )
                }) {
                    Text("Import font from phone", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Size: ${sizeSp.toInt()}", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = sizeSp,
                    onValueChange = { sizeSp = it },
                    valueRange = 8f..72f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5238),
                        activeTrackColor = Color(0xFFFF5238)
                    )
                )

                DialogTitle("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Text", selected = colorTarget == 0, color = Color(0xFFFF5238)) { colorTarget = 0 }
                    Chip("Outline", selected = colorTarget == 1, color = Color(0xFFFF5238)) { colorTarget = 1 }
                    if (background) {
                        Chip("Background", selected = colorTarget == 2, color = Color(0xFFFF5238)) { colorTarget = 2 }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val palette = if (colorTarget == 2) BackgroundColors else TextColors
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { c ->
                        val current = when (colorTarget) {
                            0 -> colorArgb
                            1 -> outlineColor
                            else -> bgColor
                        }
                        ColorDot(c, selected = current == c) {
                            when (colorTarget) {
                                0 -> colorArgb = c
                                1 -> outlineColor = c
                                else -> bgColor = c
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                ColorPlate(
                    color = when (colorTarget) {
                        0 -> colorArgb
                        1 -> outlineColor
                        else -> bgColor
                    },
                    showAlpha = colorTarget == 2,
                    onChange = { c ->
                        when (colorTarget) {
                            0 -> colorArgb = c
                            1 -> outlineColor = c
                            else -> bgColor = c
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Outline width: ${"%.1f".format(outlineWidth)} dp", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = outlineWidth,
                    onValueChange = { outlineWidth = it },
                    valueRange = 0f..6f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5238),
                        activeTrackColor = Color(0xFFFF5238)
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bold, onCheckedChange = { bold = it })
                    Text("Bold", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Checkbox(checked = background, onCheckedChange = {
                        background = it
                        if (!it && colorTarget == 2) colorTarget = 0
                    })
                    Text("Background", color = Color.White, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                DialogTitle("Position")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Top" to 0.08f, "Middle" to 0.45f, "Bottom" to 0.8f).forEach { (label, y) ->
                        Chip(label, selected = yFrac == y, color = Color(0xFFFF5238)) { yFrac = y }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                DialogTitle("Animation")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TextAnimations.forEach { (key, label) ->
                        Chip(label, selected = anim == key, color = Color(0xFF7C4DFF)) { anim = key }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Duration: ${"%.1f".format(durationMs / 1000f)} s  (drag the text's edges on the timeline to change start/end)",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Slider(
                    value = durationMs,
                    onValueChange = { durationMs = it },
                    valueRange = 500f..10000f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5238),
                        activeTrackColor = Color(0xFFFF5238)
                    )
                )

                if (initial != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        TextTrack.splitById(initial.id, TimelineModel.playheadMs.value)
                        onDismiss()
                    }) {
                        Text("Cut at playhead", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DialogTitle("Preview")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0C10)),
                    contentAlignment = Alignment.Center
                ) {
                    val family = FontLibrary.family(context, fontId)
                    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
                    val inner = if (background) {
                        Modifier
                            .background(Color(bgColor), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    }
                    Box(modifier = inner) {
                        if (outlineWidth > 0f) {
                            Text(
                                text = text.ifBlank { "Text" },
                                style = TextStyle(
                                    fontFamily = family,
                                    fontSize = sizeSp.sp,
                                    fontWeight = weight,
                                    drawStyle = Stroke(width = outlineWidth * 2f, join = StrokeJoin.Round)
                                ),
                                color = Color(outlineColor)
                            )
                        }
                        Text(
                            text = text.ifBlank { "Text" },
                            fontFamily = family,
                            fontSize = sizeSp.sp,
                            fontWeight = weight,
                            color = Color(colorArgb)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TextClip(
                            id = initial?.id ?: -1L,
                            text = text.ifBlank { "Text" },
                            startMs = initial?.startMs ?: defaultStartMs,
                            durationMs = durationMs.toLong(),
                            fontId = fontId,
                            sizeSp = sizeSp,
                            colorArgb = colorArgb,
                            bold = bold,
                            background = background,
                            xFrac = initial?.xFrac ?: 0.08f,
                            yFrac = yFrac,
                            outlineColor = outlineColor,
                            outlineWidth = outlineWidth,
                            bgColor = bgColor,
                            anim = anim
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238))
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA0A0B2))
            }
        }
    )
}

@Composable
private fun DialogTitle(label: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Chip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else Color(0xFF222230))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun ColorDot(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color(0xFFFF5238) else Color(0xFF555566),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}
