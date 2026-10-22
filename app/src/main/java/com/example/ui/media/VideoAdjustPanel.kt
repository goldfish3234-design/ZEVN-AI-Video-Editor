package com.example.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * The height is limited, so the video preview above it always stays visible.
 * Effects are in their own panel (VideoEffectsPanel).
 */
@Composable
fun VideoAdjustPanel(modifier: Modifier = Modifier) {
    val adj by VideoAdjustStore.adjust.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .heightIn(max = 230.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoFilters.list.forEach { (key, label) ->
                AdjustChip(label, selected = adj.filter == key) {
                    VideoAdjustStore.set(VideoFilters.preset(key).copy(effect = adj.effect))
                }
            }
        }

        AdjustSlider(
            label = "Brightness",
            value = adj.brightness,
            onChange = { VideoAdjustStore.set(adj.copy(brightness = it, filter = "custom")) }
        )
        AdjustSlider(
            label = "Contrast",
            value = adj.contrast,
            onChange = { VideoAdjustStore.set(adj.copy(contrast = it, filter = "custom")) }
        )
        AdjustSlider(
            label = "Saturation",
            value = adj.saturation,
            onChange = { VideoAdjustStore.set(adj.copy(saturation = it, filter = "custom")) }
        )

        TextButton(onClick = { VideoAdjustStore.set(VideoAdjust(effect = adj.effect)) }) {
            Text("Reset colour", color = Color(0xFFA0A0B2), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AdjustChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFFF5238) else Color(0xFF222230))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(78.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF5238),
                activeTrackColor = Color(0xFFFF5238)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("${(value * 100).toInt()}", color = Color(0xFFA0A0B2), fontSize = 11.sp, modifier = Modifier.width(34.dp))
    }
}
