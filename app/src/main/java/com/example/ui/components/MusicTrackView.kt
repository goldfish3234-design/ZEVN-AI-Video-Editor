package com.example.ui.components

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
import com.example.ui.media.MusicTrack

private const val HANDLE_PX = 48f

/**
 * Audio track: each audio clip is a block at its offset.
 * - Tap a clip to select it.
 * - Drag the middle of a clip to move it; drag its left or right edge to trim.
 * [modifier] should set the width (it follows the timeline zoom). The track is 30 dp tall.
 */
@Composable
fun MusicTrackView(
    clips: List<MusicTrack>,
    totalMs: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onMoveBy: (Int, Long) -> Unit,
    onTrimStartBy: (Int, Long) -> Unit,
    onTrimEndBy: (Int, Long) -> Unit,
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
        clips.forEachIndexed { i, clip ->
            val startFrac = (clip.offsetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val visibleLen = minOf(clip.lengthMs, total - clip.offsetMs).coerceAtLeast(0L)
            val lenFrac = (visibleLen.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val color = if (i == selectedIndex) Color(0xFFFF7A5C) else Color(0xFFFF5238)

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
                                        1 -> onTrimStartBy(i, deltaMs)
                                        else -> onTrimEndBy(i, deltaMs)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Audio ${clip.lengthMs / 1000}s",
                    fontSize = 9.sp,
                    color = Color.White,
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
