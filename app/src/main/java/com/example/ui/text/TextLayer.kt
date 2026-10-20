package com.example.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text overlays on the preview. Tap a text to select it, drag it anywhere on the preview to move it.
 * The position is saved as a fraction of the preview (x and y), so it stays the same in the export.
 */
@Composable
fun DraggableTextLayer(
    texts: List<TextClip>,
    playheadMs: Long,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        // remember the preview width so the export can size the text the same way
        TextPreviewMetrics.widthDp = maxWidth.value
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        texts.forEachIndexed { i, t ->
            val state = TextAnimMath.stateAt(t, playheadMs)
            if (state != null) {
                DraggableTextItem(
                    t = t,
                    index = i,
                    selected = i == selectedIndex,
                    state = state,
                    w = maxWidth,
                    h = maxHeight,
                    wPx = wPx,
                    hPx = hPx
                )
            }
        }
    }
}

@Composable
private fun DraggableTextItem(
    t: TextClip,
    index: Int,
    selected: Boolean,
    state: TextAnimMath.State,
    w: Dp,
    h: Dp,
    wPx: Float,
    hPx: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val family = remember(t.fontId, t.id) { FontLibrary.family(context, t.fontId) }
    val outlinePx = with(density) { t.outlineWidth.dp.toPx() }
    val weight = if (t.bold) FontWeight.Bold else FontWeight.Normal
    val bg = if (t.background) {
        Modifier
            .background(Color(t.bgColor), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier
    }
    val selectedBorder = if (selected) {
        Modifier.border(2.dp, Color(0xFFFFB800), RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .offset(x = w * t.xFrac, y = h * t.yFrac + state.dyDp.dp)
            .graphicsLayer {
                alpha = if (t.anim == "none") 1f else state.alpha
                scaleX = state.scale
                scaleY = state.scale
            }
            .then(bg)
            .then(selectedBorder)
            .pointerInput(index, wPx, hPx) {
                detectTapGestures(onTap = { TextTrack.select(index) })
            }
            .pointerInput(index, wPx, hPx) {
                detectDragGestures(
                    onDragStart = { TextTrack.select(index) },
                    onDrag = { change, drag ->
                        change.consume()
                        if (wPx > 0f && hPx > 0f) {
                            val cur = TextTrack.texts.value.getOrNull(index) ?: return@detectDragGestures
                            TextTrack.replace(
                                index,
                                cur.copy(
                                    xFrac = (cur.xFrac + drag.x / wPx).coerceIn(-0.2f, 1f),
                                    yFrac = (cur.yFrac + drag.y / hPx).coerceIn(-0.2f, 1f)
                                )
                            )
                        }
                    }
                )
            }
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
