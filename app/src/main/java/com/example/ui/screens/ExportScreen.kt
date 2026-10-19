package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ExportConfig
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

@Composable
fun ExportScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.currentProject.collectAsState()
    val renderState by viewModel.renderState.collectAsState()

    var resolution by remember { mutableStateOf("1080p Full HD") }
    var format by remember { mutableStateOf("MP4 (H.264)") }
    var fps by remember { mutableStateOf(60) }
    var removeWatermark by remember { mutableStateOf(true) }
    var hdrEnabled by remember { mutableStateOf(true) }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Export Center",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            // Project Preview Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = project?.thumbnailRes ?: R.drawable.zevn_sample_video),
                            contentDescription = null,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = project?.title ?: "Untitled Project",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Duration: ${(project?.durationMs ?: 18000L) / 1000}s • ${project?.aspectRatio?.label?.split(" ")?.firstOrNull() ?: "9:16"}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Active Filter: ${project?.activeFilter?.title ?: "Cyberpunk"}",
                                fontSize = 10.sp,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Export Settings
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Resolution & Master Quality",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val resolutions = listOf("720p HD", "1080p Full HD", "2K QHD", "4K Ultra HD")
                    resolutions.forEach { res ->
                        val isSelected = resolution == res
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else SurfaceVariantDark)
                                .border(1.dp, if (isSelected) ElectricBlue else CardBorder, RoundedCornerShape(8.dp))
                                .clickable { resolution = res }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(res, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextWhite)
                            if (res == "4K Ultra HD") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonPink.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("BEST QUALITY", fontSize = 9.sp, fontWeight = FontWeight.Black, color = NeonPink)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Container Format", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("MP4 (H.264)", "MOV (ProRes)", "WebM").forEach { fmt ->
                            val isSelected = format == fmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricBlue else SurfaceElevated)
                                    .clickable { format = fmt }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt.split(" ").firstOrNull() ?: fmt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Frame Rate (FPS)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(24, 30, 60).forEach { f ->
                            val isSelected = fps == f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricBlue else SurfaceElevated)
                                    .clickable { fps = f }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$f fps",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Remove ZEVN Watermark", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextWhite)
                            Text("Export 100% clean video for client or social delivery", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = removeWatermark,
                            onCheckedChange = { removeWatermark = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricBlue
                            )
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Live Render Progress Box or Start Export Button
            item {
                if (renderState.isRendering) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF0F1626),
                        borderColor = ElectricBlue
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { renderState.progress },
                                color = NeonCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Rendering in Progress... ${(renderState.progress * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = renderState.currentPhase,
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { renderState.progress },
                            color = ElectricBlue,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                } else if (renderState.isCompleted) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF10241A),
                        borderColor = NeonGreen
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Export Successfully Completed!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                                Text("Saved as ${renderState.exportedUri}", fontSize = 11.sp, color = NeonGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Share Directly to Social Platforms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out this video edited with ZEVN AI!")
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Video"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("TikTok", fontSize = 11.sp, color = TextWhite)
                            }
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Ready to upload to YouTube Shorts!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Shorts", fontSize = 11.sp, color = TextWhite)
                            }
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Ready to share to Instagram Reels!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reels", fontSize = 11.sp, color = TextWhite)
                            }
                        }
                    }
                } else {
                    NeonButton(
                        text = "Start High-Speed Export",
                        icon = Icons.Default.RocketLaunch,
                        onClick = {
                            val cfg = ExportConfig(
                                resolution = resolution,
                                format = format,
                                fps = fps,
                                removeWatermark = removeWatermark,
                                hdrEnabled = hdrEnabled
                            )
                            viewModel.startExportSimulation(cfg)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "start_export_button"
                    )
                }
            }
        }
    }
}
