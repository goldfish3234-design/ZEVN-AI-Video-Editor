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
 * Filter presets, colour effects, and the brightness, contrast and saturation sliders.
 * Every change shows in the live colour preview and is applied to the exported video.
 */
@Composable
fun VideoAdjustPanel(modifier: Modifier = Modifier) {
    val adj by VideoAdjustStore.adjust.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        ChipRow {
            VideoFilters.list.forEach { (key, label) ->
                Chip(label, selected = adj.filter == key) {
                    VideoAdjustStore.set(VideoFilters.preset(key).copy(effect = adj.effect))
                }
            }
        }

        Text("Effects", color = Color(0xFFA0A0B2), fontSize = 11.sp)
        ChipRow {
            VideoEffects.list.forEach { (key, label) ->
                Chip(label, selected = adj.effect == key) {
                    VideoAdjustStore.set(adj.copy(effect = key))
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

        TextButton(onClick = { VideoAdjustStore.reset() }) {
            Text("Reset all", color = Color(0xFFA0A0B2), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp, top = 4.dp, bottom = 4.dp)
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
