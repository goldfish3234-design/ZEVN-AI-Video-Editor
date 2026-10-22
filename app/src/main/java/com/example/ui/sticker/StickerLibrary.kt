package com.example.ui.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.media.BackgroundRemover
import com.example.ui.media.CutoutVideo
import com.example.ui.media.MediaProbe
import com.example.ui.media.TimelineModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in sticker library. The pictures are drawn by the app the first time and saved as PNG
 * (with a transparent background) in the app storage, so nothing has to be downloaded.
 */
object StickerLibrary {

    private const val SIZE = 256f

    private data class Shape(val key: String, val label: String, val color: Int)

    private val shapes = listOf(
        Shape("heart", "Heart", 0xFFFF2A55.toInt()),
        Shape("star", "Star", 0xFFFFC233.toInt()),
        Shape("sparkle", "Sparkle", 0xFFFFFFFF.toInt()),
        Shape("arrow", "Arrow", 0xFF00E5FF.toInt()),
        Shape("check", "Check", 0xFF00E676.toInt()),
        Shape("cross", "Cross", 0xFFFF5252.toInt()),
        Shape("speech", "Speech", 0xFFFFFFFF.toInt()),
        Shape("badge", "Badge", 0xFF7C4DFF.toInt())
    )

    /** (label, file uri) for every sticker. Creates the pictures the first time. */
    fun items(context: Context): List<Pair<String, String>> {
        val dir = File(context.filesDir, "sticker_lib").apply { mkdirs() }
        return shapes.map { s ->
            val f = File(dir, "${s.key}.png")
            if (!f.exists() || f.length() == 0L) render(s, f)
            s.label to Uri.fromFile(f).toString()
        }
    }

    private fun render(s: Shape, file: File) {
        val bmp = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = SIZE * 0.14f
        }
        val half = SIZE / 2f

        when (s.key) {
            "heart" -> {
                c.drawCircle(SIZE * 0.35f, SIZE * 0.38f, SIZE * 0.22f, fill)
                c.drawCircle(SIZE * 0.65f, SIZE * 0.38f, SIZE * 0.22f, fill)
                val p = Path()
                p.moveTo(SIZE * 0.14f, SIZE * 0.47f)
                p.lineTo(half, SIZE * 0.9f)
                p.lineTo(SIZE * 0.86f, SIZE * 0.47f)
                p.close()
                c.drawPath(p, fill)
            }
            "star" -> c.drawPath(starPath(half, half, SIZE * 0.48f, SIZE * 0.2f, 5), fill)
            "sparkle" -> c.drawPath(starPath(half, half, SIZE * 0.48f, SIZE * 0.1f, 4), fill)
            "arrow" -> {
                val p = Path()
                p.moveTo(SIZE * 0.1f, SIZE * 0.4f)
                p.lineTo(SIZE * 0.6f, SIZE * 0.4f)
                p.lineTo(SIZE * 0.6f, SIZE * 0.2f)
                p.lineTo(SIZE * 0.9f, SIZE * 0.5f)
                p.lineTo(SIZE * 0.6f, SIZE * 0.8f)
                p.lineTo(SIZE * 0.6f, SIZE * 0.6f)
                p.lineTo(SIZE * 0.1f, SIZE * 0.6f)
                p.close()
                c.drawPath(p, fill)
            }
            "check" -> {
                val p = Path()
                p.moveTo(SIZE * 0.2f, SIZE * 0.52f)
                p.lineTo(SIZE * 0.42f, SIZE * 0.74f)
                p.lineTo(SIZE * 0.8f, SIZE * 0.3f)
                c.drawPath(p, stroke)
            }
            "cross" -> {
                c.drawLine(SIZE * 0.25f, SIZE * 0.25f, SIZE * 0.75f, SIZE * 0.75f, stroke)
                c.drawLine(SIZE * 0.75f, SIZE * 0.25f, SIZE * 0.25f, SIZE * 0.75f, stroke)
            }
            "speech" -> {
                c.drawRoundRect(RectF(SIZE * 0.1f, SIZE * 0.15f, SIZE * 0.9f, SIZE * 0.7f), SIZE * 0.12f, SIZE * 0.12f, fill)
                val tail = Path()
                tail.moveTo(SIZE * 0.25f, SIZE * 0.66f)
                tail.lineTo(SIZE * 0.3f, SIZE * 0.9f)
                tail.lineTo(SIZE * 0.5f, SIZE * 0.66f)
                tail.close()
                c.drawPath(tail, fill)
            }
            "badge" -> {
                c.drawCircle(half, half, SIZE * 0.46f, fill)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = SIZE * 0.06f
                }
                c.drawCircle(half, half, SIZE * 0.3f, ring)
            }
        }

        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bmp.recycle()
    }

    /** A star with [points] points. The inner radius sets how sharp the points are. */
    private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path {
        val p = Path()
        val steps = points * 2
        for (i in 0 until steps) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / points
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }
}

/**
 * Grid of built-in stickers. Tap one to place it on the preview at the playhead.
 * Also here: "Remove background from a photo" (a sticker) and "Cut out from a video" (an animated sticker).
 * [onImport] opens the phone's picker for your own picture.
 */
@Composable
fun StickerLibraryDialog(
    onPick: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { StickerLibrary.items(context) }
    var busy by remember { mutableStateOf(false) }
    // Cut out from a video: -1 = idle, otherwise the progress 0..100
    var cutProgress by remember { mutableIntStateOf(-1) }

    // Remove BG: a photo is picked, the subject is cut out, and the cut-out is used as a sticker
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                val file = withContext(Dispatchers.IO) { BackgroundRemover.removeToFile(context, uri) }
                busy = false
                if (file != null) {
                    onPick(Uri.fromFile(file).toString())
                } else {
                    Toast.makeText(context, "ছবি থেকে ব্যাকগ্রাউন্ড মোছা যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Cut out from a video: the subject of the video is cut frame by frame and placed as an animated sticker
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && cutProgress < 0) {
            cutProgress = 0
            scope.launch {
                val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, uri) }
                val frames = withContext(Dispatchers.IO) {
                    CutoutVideo.generate(context, uri, 0L, duration) { p ->
                        scope.launch { cutProgress = p }
                    }
                }
                cutProgress = -1
                if (frames != null) {
                    StickerTrack.addAnimated(frames, CutoutVideo.FPS, TimelineModel.playheadMs.value)
                    Toast.makeText(context, "ভিডিও থেকে কাটা অংশ যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    Toast.makeText(context, "ভিডিও থেকে কাটা যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161622),
        title = { Text("Stickers", color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                items.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, uri) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF222230))
                                    .clickable { onPick(uri) }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = label,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(label, color = Color.White, fontSize = 10.sp)
                            }
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onImport, enabled = !busy && cutProgress < 0) {
                    Text("Import from phone", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { bgPicker.launch(arrayOf("image/*")) },
                    enabled = !busy && cutProgress < 0
                ) {
                    Text(
                        if (busy) "ব্যাকগ্রাউন্ড মোছা হচ্ছে..." else "Remove background from a photo",
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { videoPicker.launch(arrayOf("video/*")) },
                    enabled = !busy && cutProgress < 0
                ) {
                    Text(
                        if (cutProgress >= 0) "ভিডিও কাটা হচ্ছে... $cutProgress%" else "Cut out from a video (up to 10 s)",
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFFA0A0B2))
            }
        }
    )
}
