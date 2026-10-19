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

/**
 * Music shown as a bar on its own track. The bar sits at the music offset and is as long as the
 * trimmed music. Drag the bar left or right to move the music on the timeline.
 */
@Composable
fun MusicTrackView(
    music: MusicTrack,
    totalMs: Long,
    onMoveBy: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (totalMs <= 0L) 1L else totalMs
    val startFrac = (music.offsetMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val visibleLen = min2(music.lengthMs, total - music.offsetMs).coerceAtLeast(0L)
    val lenFrac = (visibleLen.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val endSpace = max(1f - startFrac - lenFrac, 0.0001f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14141C))
            .pointerInput(total) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (size.width > 0) {
                        val deltaMs = (drag.x / size.width.toFloat() * total.toFloat()).toLong()
                        onMoveBy(deltaMs)
                    }
                }
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
                    .background(Color(0xFFFF5238)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Music ${music.lengthMs / 1000}s",
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(modifier = Modifier.weight(endSpace))
        }
    }
}

private fun min2(a: Long, b: Long): Long = if (a < b) a else b
