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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Filter chips and the brightness, contrast and saturation sliders.
 * The values are saved with the project and applied to the exported video.
 */
@Composable
fun VideoAdjustPanel(modifier: Modifier = Modifier) {
    val adj by VideoAdjustStore.adjust.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoFilters.list.forEach { (key, label) ->
                val chosen = adj.filter == key
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (chosen) Color(0xFFFF5238) else Color(0xFF222230))
                        .clickable { VideoAdjustStore.set(VideoFilters.preset(key)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            Text("Reset", color = Color(0xFFA0A0B2), fontSize = 12.sp)
        }
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
