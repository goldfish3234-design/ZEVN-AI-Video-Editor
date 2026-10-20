package com.example.ui.text

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val EDGE_PX = 48f
private const val MIN_TEXT_MS = 300L

/**
 * Text track, drawn the same way as the video and audio tracks:
 * a selected block, white edge handles, tap to select, drag the middle to move,
 * drag the left or right edge to change the start or end.
 */
@Composable
fun TextTrackBar(
    texts: List<TextClip>,
    totalMs: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141C))
    ) {
        val trackWidth = maxWidth
        texts.forEachIndexed { i, t ->
            val startFrac = (t.startMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val lenFrac = (t.durationMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val color = if (i == selectedIndex) Color(0xFFFFC233) else Color(0xFFB8860B)
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * startFrac)
                    .width(maxOf(trackWidth * lenFrac, 6.dp))
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
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
                                        0 -> TextTrack.moveBy(i, deltaMs, total)
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
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(5.dp)
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(5.dp)
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

/** Moves the start of the text at [index] to the playhead (keeps its end). */
fun textTrimStartToPlayhead(index: Int, playheadMs: Long) {
    val c = TextTrack.texts.value.getOrNull(index) ?: return
    val end = c.startMs + c.durationMs
    if (playheadMs <= c.startMs || playheadMs > end - MIN_TEXT_MS) return
    TextTrack.replace(index, c.copy(startMs = playheadMs, durationMs = end - playheadMs))
}

/** Moves the end of the text at [index] to the playhead (keeps its start). */
fun textTrimEndToPlayhead(index: Int, playheadMs: Long) {
    val c = TextTrack.texts.value.getOrNull(index) ?: return
    if (playheadMs < c.startMs + MIN_TEXT_MS || playheadMs >= c.startMs + c.durationMs) return
    TextTrack.replace(index, c.copy(durationMs = playheadMs - c.startMs))
}
