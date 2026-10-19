package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.example.ui.media.MusicTrack
import kotlin.math.max

private const val HANDLE_PX = 48f

/**
 * Music bar on its own track, sized to the trimmed music and placed at its offset.
 * - Drag the middle to move the music on the timeline.
 * - Drag the left edge to trim the start, or the right edge to trim the end.
 * [modifier] should set the width (it follows the timeline zoom).
 */
@Composable
fun MusicTrackView(
    music: MusicTrack,
    totalMs: Long,
    selected: Boolean,
    onGrab: () -> Unit,
    onMoveBy: (Long) -> Unit,
    onTrimStartBy: (Long) -> Unit,
    onTrimEndBy: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    val startFrac = (music.offsetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val visibleLen = minOf(music.lengthMs, total - music.offsetMs).coerceAtLeast(0L)
    val lenFrac = (visibleLen.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val endSpace = max(1f - startFrac - lenFrac, 0.0001f)
    val barColor = if (selected) Color(0xFFFF7A5C) else Color(0xFFFF5238)

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141C))
            .pointerInput(total) {
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
                        onGrab()
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (size.width > 0) {
                            val deltaMs = (drag.x / size.width.toFloat() * total.toFloat()).toLong()
                            when (mode) {
                                0 -> onMoveBy(deltaMs)
                                1 -> onTrimStartBy(deltaMs)
                                else -> onTrimEndBy(deltaMs)
                            }
                        }
                    }
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (startFrac > 0f) Spacer(modifier = Modifier.weight(max(startFrac, 0.0001f)))
            Box(
                modifier = Modifier
                    .weight(max(lenFrac, 0.0001f))
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Music ${music.lengthMs / 1000}s",
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                // Left handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(5.dp)
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
                // Right handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(5.dp)
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
            Spacer(modifier = Modifier.weight(endSpace))
        }
    }
}
