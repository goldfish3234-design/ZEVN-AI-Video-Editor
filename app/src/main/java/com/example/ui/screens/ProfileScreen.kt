package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

@Composable
fun ProfileScreen(
    viewModel: VideoEditorViewModel,
    onNavigateToPricing: () -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val projects by viewModel.projects.collectAsState()

    var showRefillModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Creator Profile & Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            // Admin Panel Shortcut Button
            IconButton(
                onClick = onNavigateToAdmin,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Panel", tint = NeonCyan)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Profile Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ZevnElectricGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AV",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(userProfile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                                Text(userProfile.email, fontSize = 12.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ElectricBlue.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(userProfile.subscriptionTier, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onNavigateToPricing,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DividerDark)
                    Spacer(modifier = Modifier.height(16.dp))

                    // AI Credits Balance Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AI Credits Balance", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                            Text("${userProfile.aiCredits} of ${userProfile.maxCredits} credits available", fontSize = 11.sp, color = TextMuted)
                        }
                        Button(
                            onClick = { showRefillModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = NeonAmber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Refill", fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val creditProgress = (userProfile.aiCredits.toFloat() / userProfile.maxCredits.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { creditProgress },
                        color = NeonAmber,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Storage Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cloud Storage", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                        Text("${userProfile.usedStorageGb} GB / ${userProfile.maxStorageGb} GB", fontSize = 11.sp, color = NeonCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val storageProgress = (userProfile.usedStorageGb / userProfile.maxStorageGb).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { storageProgress },
                        color = ElectricBlue,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Creator Performance Stats
            item {
                Text("Creator Performance & Analytics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text("${userProfile.totalVideosCreated}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextWhite)
                        Text("Videos Rendered", fontSize = 11.sp, color = TextMuted)
                    }
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text(userProfile.estimatedViews, fontWeight = FontWeight.Black, fontSize = 20.sp, color = NeonGreen)
                        Text("Audience Reach", fontSize = 11.sp, color = TextMuted)
                    }
                    GlassCard(modifier = Modifier.weight(1f), backgroundColor = SurfaceVariantDark) {
                        Text(userProfile.hoursSaved, fontWeight = FontWeight.Black, fontSize = 20.sp, color = NeonCyan)
                        Text("Hours Saved", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Saved Drafts & Export History
            item {
                Text("Saved Drafts & Projects (${projects.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                projects.forEach { proj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoLibrary, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(proj.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                                Text("${proj.resolution} • ${proj.durationMs / 1000}s • ${proj.status}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        IconButton(
                            onClick = { Toast.makeText(context, "Exporting ${proj.title}", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Download, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showRefillModal) {
        AlertDialog(
            onDismissRequest = { showRefillModal = false },
            containerColor = SurfaceElevated,
            title = { Text("Refill AI Credits", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Select a credit pack to instantly power your AI Auto-Edit, Voice Cloning, and Veo Motion generations.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "+500 AI Credits added to account!", Toast.LENGTH_SHORT).show()
                        showRefillModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("+500 Credits ($4.99)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefillModal = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
