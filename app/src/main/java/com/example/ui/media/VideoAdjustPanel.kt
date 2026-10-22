package com.example.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour adjustment only: filter presets, brightness, contrast and saturation.
 * Kept compact (about 150 dp tall) so the picture above it stays large.
 * Effects are in their own panel (VideoEffectsPanel).
 */
@Composable
fun VideoAdjustPanel(modifier: Modifier = Modifier) {
    val adj by VideoAdjustStore.adjust.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .heightIn(max = 150.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoFilters.list.forEach { (key, label) ->
                CompactChip(label, selected = adj.filter == key) {
                    VideoAdjustStore.set(VideoFilters.preset(key).copy(effect = adj.effect))
                }
            }
            CompactChip("Reset", selected = false) {
                VideoAdjustStore.set(VideoAdjust(effect = adj.effect))
            }
        }

        CompactSlider(
            label = "Brightness",
            value = adj.brightness,
            onChange = { VideoAdjustStore.set(adj.copy(brightness = it, filter = "custom")) }
        )
        CompactSlider(
            label = "Contrast",
            value = adj.contrast,
            onChange = { VideoAdjustStore.set(adj.copy(contrast = it, filter = "custom")) }
        )
        CompactSlider(
            label = "Saturation",
            value = adj.saturation,
            onChange = { VideoAdjustStore.set(adj.copy(saturation = it, filter = "custom")) }
        )
    }
}

@Composable
private fun CompactChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFFF5238) else Color(0xFF222230))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.width(66.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF5238),
                activeTrackColor = Color(0xFFFF5238)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        Text("${(value * 100).toInt()}", color = Color(0xFFA0A0B2), fontSize = 10.sp, modifier = Modifier.width(28.dp))
    }
}
