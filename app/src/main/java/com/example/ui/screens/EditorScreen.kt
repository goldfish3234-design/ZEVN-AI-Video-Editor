package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.MusicTrackView
import com.example.ui.components.TimelineView
import com.example.ui.components.VideoPreview
import com.example.ui.media.ExportOptions
import com.example.ui.media.MediaProbe
import com.example.ui.media.TimelineExporter
import com.example.ui.media.TimelineModel
import com.example.ui.media.VideoAudio
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenExport: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val project by viewModel.currentProject.collectAsState()
    val sources by TimelineModel.sources.collectAsState()
    val segments by TimelineModel.segments.collectAsState()
    val selected by TimelineModel.selected.collectAsState()
    val playhead by TimelineModel.playheadMs.collectAsState()
    val isPlaying by TimelineModel.isPlaying.collectAsState()
    val canUndo by TimelineModel.canUndo.collectAsState()
    val canRedo by TimelineModel.canRedo.collectAsState()
    val music by TimelineModel.music.collectAsState()
    val videoVolume by VideoAudio.volume.collectAsState()

    val currentProj = project ?: return

    val exporter = remember { TimelineExporter(context) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }

    // Stop playback when leaving the editor; cancel export if leaving
    DisposableEffect(Unit) {
        onDispose {
            TimelineModel.setPlaying(false)
            exporter.cancel()
        }
    }

    // Poll export progress while exporting
    LaunchedEffect(exporting) {
        while (exporting) {
            exportProgress = exporter.progress()
            delay(250L)
        }
    }

    // Multiple video import (Android Photo Picker)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                for (uri in uris) {
                    val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, uri) }
                    TimelineModel.addSource(uri, duration)
                }
            }
        }
    }
    val launchVideoPicker: () -> Unit = {
        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    // Background music (audio file)
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, uri) }
                TimelineModel.setMusic(uri, duration)
            }
        }
    }
    val launchMusicPicker: () -> Unit = {
        musicPicker.launch(arrayOf("audio/*"))
    }

    val startExport: () -> Unit = {
        if (segments.isNotEmpty() && !exporting) {
            exporting = true
            exportProgress = 0
            exporter.export(
                sources = sources,
                segments = segments,
                music = music,
                options = ExportOptions(
                    aspectRatio = currentProj.aspectRatio.ratio,
                    rotationDeg = currentProj.rotationAngle.toFloat(),
                    flipH = currentProj.isFlippedH,
                    flipV = currentProj.isFlippedV,
                    videoVolume = videoVolume
                ),
                onDone = { location ->
                    exporting = false
                    Toast.makeText(context, "Saved: $location", Toast.LENGTH_LONG).show()
                },
                onError = { message ->
                    exporting = false
                    Toast.makeText(context, "Export failed: $message", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val total = segments.sumOf { it.durationMs }
    val hasSelection = selected >= 0
    val hasVideo = segments.isNotEmpty()

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
                IconButton(onClick = launchVideoPicker) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = "Import Video", tint = Color.White)
                }
                Button(
                    onClick = startExport,
                    enabled = hasVideo && !exporting,
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
                    .clickable(enabled = hasVideo) { TimelineModel.togglePlaying() }
            ) {
                val mediaModifier = Modifier
                    .fillMaxSize()
                    .rotate(currentProj.rotationAngle.toFloat())

                if (hasVideo) {
                    VideoPreview(sources = sources, segments = segments, modifier = mediaModifier)
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.zevn_fashion_hero),
                        contentDescription = "Preview placeholder",
                        modifier = mediaModifier,
                        contentScale = ContentScale.Crop
                    )
                    Button(
                        onClick = launchVideoPicker,
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
                    .clickable(enabled = hasVideo) { TimelineModel.togglePlaying() },
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

        // --- VIDEO TRACK ---
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

        Spacer(modifier = Modifier.height(6.dp))

        // --- MUSIC TRACK ---
        val musicTrack = music
        if (musicTrack == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = launchMusicPicker, enabled = hasVideo) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Music", fontSize = 12.sp)
                }
            }
        } else {
            MusicTrackView(
                music = musicTrack,
                totalMs = total,
                onMoveBy = { delta -> TimelineModel.moveMusicBy(delta) },
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { TimelineModel.trimMusicStartToPlayhead() }) {
                    Text("Trim music start", fontSize = 11.sp, color = Color(0xFFFF5238))
                }
                TextButton(onClick = { TimelineModel.trimMusicEndToPlayhead() }) {
                    Text("Trim music end", fontSize = 11.sp, color = Color(0xFFFF5238))
                }
                IconButton(onClick = { TimelineModel.removeMusic() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Music", tint = Color(0xFFA0A0B2))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Music", fontSize = 11.sp, color = Color.White)
                Slider(
                    value = musicTrack.volume,
                    onValueChange = { TimelineModel.setMusicVolume(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5238),
                        activeTrackColor = Color(0xFFFF5238)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("${(musicTrack.volume * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFA0A0B2))
            }
        }

        // --- VIDEO SOUND ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { VideoAudio.toggleMute() },
                enabled = hasVideo
            ) {
                Icon(
                    imageVector = if (videoVolume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Video sound",
                    tint = if (hasVideo) Color.White else Color(0xFF4A4A58),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "Video sound",
                fontSize = 11.sp,
                color = Color.White
            )
            Slider(
                value = videoVolume,
                onValueChange = { VideoAudio.setVolume(it) },
                enabled = hasVideo,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF5238),
                    activeTrackColor = Color(0xFFFF5238)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                text = "${(videoVolume * 100).toInt()}%",
                fontSize = 11.sp,
                color = Color(0xFFA0A0B2)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- EDIT TOOLS ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                ToolButton("Split", Icons.Default.CallSplit, enabled = hasVideo) {
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
                ToolButton("Paste", Icons.Default.ContentPaste, enabled = hasVideo) {
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

    // --- EXPORT PROGRESS DIALOG ---
    if (exporting) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF161622),
            title = { Text("Exporting video", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Rendering video, music and sound...", fontSize = 12.sp, color = Color(0xFFA0A0B2))
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFF5238)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("$exportProgress%", fontSize = 12.sp, color = Color.White)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exporter.cancel()
                    exporting = false
                    Toast.makeText(context, "Export cancelled", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Cancel", color = Color(0xFFFF5238))
                }
            }
        )
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
