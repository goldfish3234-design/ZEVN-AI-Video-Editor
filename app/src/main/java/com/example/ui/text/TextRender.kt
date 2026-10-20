package com.example.ui.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File

/**
 * Draws one text overlay into a transparent bitmap, for the exported video.
 * [scale] converts preview sizes (dp / sp) to export pixels.
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
