package com.example.ui.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File

/** Width (in dp) of the preview box the text is drawn in. Set by the preview; used to size export text the same way. */
object TextPreviewMetrics {
    @Volatile
    var widthDp: Float = 0f
}

/** Animation timing shared by the preview and the export. */
object TextAnimMath {
    const val ANIM_MS = 400L
    const val STEP_MS = 50L

    /** How a text looks at one moment: opacity, vertical shift (dp, down is +), size factor. */
    data class State(val alpha: Float, val dyDp: Float, val scale: Float)

    /** Returns the look of [t] at [timeMs], or null if the text is not shown then. */
    fun stateAt(t: TextClip, timeMs: Long): State? {
        val local = (timeMs - t.startMs).toFloat()
        if (local < 0f || local >= t.durationMs) return null
        val inP = (local / ANIM_MS).coerceIn(0f, 1f)
        val outP = ((t.durationMs - local) / ANIM_MS).coerceIn(0f, 1f)
        val presence = minOf(inP, outP)
        return when (t.anim) {
            "fade" -> State(presence, 0f, 1f)
            "slide" -> State(presence, (1f - inP) * 40f, 1f)
            "drop" -> State(presence, -(1f - inP) * 60f, 1f)
            "pop" -> State(presence, 0f, if (inP < 1f) 0.3f + 0.7f * inP else 1f)
            else -> State(1f, 0f, 1f)
        }
    }

    /** Moments where the look of [t] changes, so the export can cut the video there. */
    fun breakpoints(t: TextClip): List<Long> {
        if (t.anim == "none") return emptyList()
        val steps = (ANIM_MS / STEP_MS).toInt()
        val result = mutableListOf<Long>()
        for (k in 0..steps) {
            result.add(t.startMs + k * STEP_MS)
            result.add(t.startMs + t.durationMs - k * STEP_MS)
        }
        return result
    }
}

/**
 * Draws one text overlay into a transparent bitmap, for the exported video.
 * [scale] converts preview dp / sp to export pixels.
 */
object TextRenderer {

    fun render(context: Context, t: TextClip, scale: Float): Bitmap {
        val typeface = typefaceFor(context, t.fontId, t.bold)
        val textSize = t.sizeSp * scale
        val extra = t.outlineWidth * scale
        val pad = if (t.background) 6f * scale else 0f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
            color = t.colorArgb.toInt()
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
            color = t.outlineColor.toInt()
            style = Paint.Style.STROKE
            strokeWidth = extra * 2f
            strokeJoin = Paint.Join.ROUND
        }

        val lines = t.text.split("\n")
        val fm = fill.fontMetrics
        val lineH = fm.descent - fm.ascent
        val maxW = lines.maxOf { fill.measureText(it) }

        val w = (maxW + pad * 2f + extra * 2f).toInt().coerceAtLeast(1)
        val h = (lineH * lines.size + pad * 2f + extra * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (t.background) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.bgColor.toInt()
                style = Paint.Style.FILL
            }
            val radius = 8f * scale
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bg)
        }

        var y = pad + extra - fm.ascent
        for (line in lines) {
            val x = pad + extra
            if (t.outlineWidth > 0f) canvas.drawText(line, x, y, stroke)
            canvas.drawText(line, x, y, fill)
            y += lineH
        }
        return bitmap
    }

    fun typefaceFor(context: Context, fontId: String, bold: Boolean): Typeface {
        val base: Typeface = when {
            fontId.startsWith("file:") -> {
                val f = File(File(context.filesDir, "fonts"), fontId.removePrefix("file:"))
                try {
                    if (f.exists()) Typeface.createFromFile(f) else Typeface.DEFAULT
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            }
            fontId == "serif" -> Typeface.SERIF
            fontId == "sans" -> Typeface.SANS_SERIF
            fontId == "mono" -> Typeface.MONOSPACE
            fontId == "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
