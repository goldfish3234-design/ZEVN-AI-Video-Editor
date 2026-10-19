package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.theme.*

@Composable
fun AdminPanelScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ZEVN Admin & Cloud Console",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = TextWhite
                )
                Text(
                    text = "Cluster Health & Telemetry",
                    fontSize = 11.sp,
                    color = NeonCyan
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            // Cluster Health
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = Color(0xFF0F1E24), borderColor = ElectricBlue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("GPU Render Cluster Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                            Text("All 48 NVIDIA H100 Cloud Nodes Operational", fontSize = 11.sp, color = NeonGreen)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("99.98% UPTIME", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Active Queues", fontSize = 10.sp, color = TextMuted)
                            Text("14 jobs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                        Column {
                            Text("Avg Render Time", fontSize = 10.sp, color = TextMuted)
                            Text("2.4s (4K)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        Column {
                            Text("AI Engine Load", fontSize = 10.sp, color = TextMuted)
                            Text("38%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Revenue & Subscription Analytics
            item {
                Text("Revenue & Platform Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text("$142.8K", fontWeight = FontWeight.Black, fontSize = 18.sp, color = NeonGreen)
                        Text("Monthly MRR", fontSize = 11.sp, color = TextMuted)
                    }
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text("28,490", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextWhite)
                        Text("Active Creators", fontSize = 11.sp, color = TextMuted)
                    }
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text("84.2%", fontWeight = FontWeight.Black, fontSize = 18.sp, color = ElectricBlue)
                        Text("Pro Retention", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // User Management Table Preview
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Creator Accounts", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                        Text("Showing 4 of 28,490", fontSize = 10.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val users = listOf(
                        Triple("alex.creator@zevn.ai", "Pro Plan", "38 videos • 420 credits"),
                        Triple("maya.studio@youtube.com", "Creator Studio", "112 videos • 1850 credits"),
                        Triple("cipher7@streamer.tv", "Creator Studio", "94 videos • 1200 credits"),
                        Triple("samuel.shorts@tiktok.com", "Starter Free", "6 videos • 80 credits")
                    )

                    users.forEach { (email, plan, meta) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(email, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text(meta, fontSize = 10.sp, color = TextMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (plan.contains("Studio")) NeonAmber.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(plan, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (plan.contains("Studio")) NeonAmber else NeonCyan)
                            }
                        }
                        HorizontalDivider(color = DividerDark, thickness = 0.5.dp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Content Moderation & Maintenance Actions
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("System & Moderation Actions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { Toast.makeText(context, "AI Moderation Filter re-synchronized!", Toast.LENGTH_SHORT).show() },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync Moderation", fontSize = 11.sp, color = TextWhite)
                        }
                        Button(
                            onClick = { Toast.makeText(context, "GPU Pipeline caches cleared!", Toast.LENGTH_SHORT).show() },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Purge Render Cache", fontSize = 11.sp, color = TextWhite)
                        }
                    }
                }
            }
        }
    }
}
