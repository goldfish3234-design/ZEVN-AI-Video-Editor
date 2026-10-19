package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.TimelineView
import com.example.ui.components.VideoPreview
import com.example.ui.media.ImportedMedia
import com.example.ui.media.TimelineModel
import com.example.ui.viewmodel.VideoEditorViewModel

@Composable
fun EditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenExport: () -> Unit
) {
    val project by viewModel.currentProject.collectAsState()
    val videoUri by ImportedMedia.videoUri.collectAsState()
    val segments by TimelineModel.segments.collectAsState()
    val selected by TimelineModel.selected.collectAsState()
    val playhead by TimelineModel.playheadMs.collectAsState()
    val isPlaying by TimelineModel.isPlaying.collectAsState()
    val canUndo by TimelineModel.canUndo.collectAsState()
    val canRedo by TimelineModel.canRedo.collectAsState()

    val currentProj = project ?: return

    // Stop playback when leaving the editor
    DisposableEffect(Unit) {
        onDispose { TimelineModel.setPlaying(false) }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            TimelineModel.resetAll()
            ImportedMedia.setVideo(uri)
        }
    }
    val launchPicker: () -> Unit = {
        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    val total = segments.sumOf { it.durationMs }
    val hasSelection = selected >= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .statusBarsPadding()
    ) {
        // --- HEADER: close, undo, redo, import, export ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close Editor", tint = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { TimelineModel.undo() }, enabled = canUndo) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color(0xFF4A4A58)
                    )
                }
                IconButton(onClick = { TimelineModel.redo() }, enabled = canRedo) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color(0xFF4A4A58)
                    )
                }
                IconButton(onClick = launchPicker) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = "Import Video", tint = Color.White)
                }
                Button(
                    onClick = onOpenExport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4834)),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // --- PREVIEW ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(currentProj.aspectRatio.ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .clickable(enabled = segments.isNotEmpty()) { TimelineModel.togglePlaying() }
            ) {
                val mediaModifier = Modifier
                    .fillMaxSize()
                    .rotate(currentProj.rotationAngle.toFloat())

                val uri = videoUri
                if (uri != null) {
                    VideoPreview(uri = uri, segments = segments, modifier = mediaModifier)
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.zevn_fashion_hero),
                        contentDescription = "Preview placeholder",
                        modifier = mediaModifier,
                        contentScale = ContentScale.Crop
                    )
                    Button(
                        onClick = launchPicker,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- TRANSPORT ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatMs(playhead)} / ${formatMs(total)}",
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = segments.isNotEmpty()) { TimelineModel.togglePlaying() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black
                )
            }
            Text(
                text = if (hasSelection) "Clip ${selected + 1}" else "No clip",
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
        }

        // --- TIMELINE ---
        TimelineView(
            segments = segments,
            selectedIndex = selected,
            playheadMs = playhead,
            totalMs = total,
            onTimeChosen = { t ->
                TimelineModel.requestSeek(t)
                TimelineModel.selectAtTime(t)
            },
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- EDIT TOOLS ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                ToolButton("Split", Icons.Default.CallSplit, enabled = segments.isNotEmpty()) {
                    TimelineModel.splitAtPlayhead()
                }
            }
            item {
                ToolButton("Trim start", Icons.Default.FirstPage, enabled = hasSelection) {
                    TimelineModel.trimStartToPlayhead()
                }
            }
            item {
                ToolButton("Trim end", Icons.Default.LastPage, enabled = hasSelection) {
                    TimelineModel.trimEndToPlayhead()
                }
            }
            item {
                ToolButton("Copy", Icons.Default.ContentCopy, enabled = hasSelection) {
                    TimelineModel.copySelected()
                }
            }
            item {
                ToolButton("Cut", Icons.Default.ContentCut, enabled = hasSelection) {
                    TimelineModel.cutSelected()
                }
            }
            item {
                ToolButton("Paste", Icons.Default.ContentPaste, enabled = segments.isNotEmpty()) {
                    TimelineModel.pasteAtPlayhead()
                }
            }
            item {
                ToolButton("Delete", Icons.Default.Delete, enabled = hasSelection) {
                    TimelineModel.deleteSelected()
                }
            }
            item {
                ToolButton("Rotate", Icons.Default.RotateRight, enabled = true) {
                    viewModel.rotate()
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) Color(0xFFD4D4DE) else Color(0xFF4A4A58)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
