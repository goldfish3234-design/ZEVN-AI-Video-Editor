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
import com.example.ui.media.Segment

/**
 * Horizontal timeline. Each segment is a block sized by its duration.
 * Tap or drag anywhere on the track to move the playhead (and select the segment there).
 */
@Composable
fun TimelineView(
    segments: List<Segment>,
    selectedIndex: Int,
    playheadMs: Long,
    totalMs: Long,
    onTimeChosen: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    val fraction = (playheadMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF14141C))
            .pointerInput(total) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onTimeChosen((offset.x / size.width * total).toLong().coerceIn(0L, total))
                    }
                }
            }
            .pointerInput(total) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onTimeChosen((change.position.x / size.width * total).toLong().coerceIn(0L, total))
                    }
                }
            }
    ) {
        // Segment blocks
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            segments.forEachIndexed { i, seg ->
                Box(
                    modifier = Modifier
                        .weight(seg.durationMs.coerceAtLeast(1L).toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (i == selectedIndex) Color(0xFFFF5238) else Color(0xFF2E2E3E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${i + 1} · ${seg.durationMs / 1000}s",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Playhead line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}
