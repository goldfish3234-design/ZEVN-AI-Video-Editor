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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One text overlay. Times are timeline ms. Position is a fraction of the preview (0..1). */
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
    val yFrac: Float
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
}

/** Colour choices for text. */
val TextColors: List<Long> = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFFFD60AL, 0xFFFF5238L, 0xFF00E5FFL, 0xFF00FF9DL, 0xFFFF9F0AL
)

/** Draws the text overlays that are visible at the playhead, on top of the video preview. */
@Composable
fun TextOverlayLayer(texts: List<TextClip>, playheadMs: Long, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        texts.forEach { t ->
            if (playheadMs >= t.startMs && playheadMs < t.startMs + t.durationMs) {
                TextOverlayItem(t, w, h)
            }
        }
    }
}

@Composable
private fun TextOverlayItem(t: TextClip, w: Dp, h: Dp) {
    val context = LocalContext.current
    val family = remember(t.fontId) { FontLibrary.family(context, t.fontId) }
    val bg = if (t.background) {
        Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier
    }
    Text(
        text = t.text,
        fontFamily = family,
        fontSize = t.sizeSp.sp,
        fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal,
        color = Color(t.colorArgb),
        textAlign = TextAlign.Start,
        modifier = Modifier
            .offset(x = w * t.xFrac, y = h * t.yFrac)
            .then(bg)
    )
}

/** Text track: one block per text overlay. Tap to select, drag to move in time. */
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
                        detectDragGestures(
                            onDragStart = { onSelect(i) },
                            onDrag = { change, drag ->
                                change.consume()
                                if (size.width > 0) {
                                    onMoveBy(i, (drag.x / size.width.toFloat() * total.toFloat()).toLong())
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

/** Dialog to add or edit a text overlay: text, font (incl. imported fonts), size, colour, style, time, position. */
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
    var bold by remember { mutableStateOf(initial?.bold ?: true) }
    var background by remember { mutableStateOf(initial?.background ?: false) }
    var durationMs by remember { mutableStateOf((initial?.durationMs ?: 3000L).toFloat()) }
    var yFrac by remember { mutableStateOf(initial?.yFrac ?: 0.8f) }
    var fonts by remember { mutableStateOf(FontLibrary.options(context)) }

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
                Text("Font", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    fonts.forEach { (id, label) ->
                        val selectedFont = fontId == id
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedFont) Color(0xFFFF5238) else Color(0xFF222230))
                                .clickable { fontId = id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp)
                        }
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

                Text("Colour", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (colorArgb == c) 3.dp else 1.dp,
                                    color = if (colorArgb == c) Color(0xFFFF5238) else Color(0xFF555566),
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bold, onCheckedChange = { bold = it })
                    Text("Bold", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Checkbox(checked = background, onCheckedChange = { background = it })
                    Text("Background", color = Color.White, fontSize = 12.sp)
                }

                Text("Position", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Top" to 0.08f, "Middle" to 0.45f, "Bottom" to 0.8f).forEach { (label, y) ->
                        val chosen = yFrac == y
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (chosen) Color(0xFFFF5238) else Color(0xFF222230))
                                .clickable { yFrac = y }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Duration: ${"%.1f".format(durationMs / 1000f)} s",
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

                Spacer(modifier = Modifier.height(8.dp))
                Text("Preview", color = Color(0xFFA0A0B2), fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0C10)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text.ifBlank { "Text" },
                        fontFamily = FontLibrary.family(context, fontId),
                        fontSize = sizeSp.sp,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        color = Color(colorArgb)
                    )
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
                            yFrac = yFrac
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
