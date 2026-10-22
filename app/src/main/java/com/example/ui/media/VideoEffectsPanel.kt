package com.example.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Colour effects only (Sepia, Grayscale, Invert, Warm/Cool tint).
 * It is a separate panel from the colour adjustment, and it is compact so the preview stays visible.
 */
@Composable
fun VideoEffectsPanel(modifier: Modifier = Modifier) {
    val adj by VideoAdjustStore.adjust.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoEffects.list.forEach { (key, label) ->
                EffectChip(label, selected = adj.effect == key) {
                    VideoAdjustStore.set(adj.copy(effect = key))
                }
            }
        }
        Text(
            "Effects change the colours of the whole video. Use Adjust for brightness, contrast and saturation.",
            color = Color(0xFFA0A0B2),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun EffectChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
