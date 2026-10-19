package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.media.ActiveTrack
import com.example.ui.media.ExportOptions
import com.example.ui.media.MediaProbe
import com.example.ui.media.ProjectState
import com.example.ui.media.ProjectStore
import com.example.ui.media.TimelineExporter
import com.example.ui.media.TimelineModel
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

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
    val activeTrack by TimelineModel.activeTrack.collectAsState()
    val playhead by TimelineModel.playheadMs.collectAsState()
    val isPlaying by TimelineModel.isPlaying.collectAsState()
    val canUndo by TimelineModel.canUndo.collectAsState()
    val canRedo by TimelineModel.canRedo.collectAsState()
    val music by TimelineModel.music.collectAsState()

    val currentProj = project ?: return

    val exporter = remember { TimelineExporter(context) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var extracting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    // The volume bar only appears after the Volume tool is pressed
    var showVolumeBar by rememberSaveable { mutableStateOf(false) }
    val timelineScroll = rememberScrollState()

    // Open the current project once (if the timeline is empty, e.g. after a restart)
    LaunchedEffect(Unit) {
        if (TimelineModel.sources.value.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { ProjectStore.loadCurrent(context) }
            if (saved != null) TimelineModel.restore(saved.sources, saved.segments, saved.music)
        }
        loaded = true
    }

    // Auto-save shortly after every change
    LaunchedEffect(sources, segments, music, loaded) {
        if (loaded) {
            delay(500L)
            val state = ProjectState(ProjectStore.currentName, sources, segments, music)
            withContext(Dispatchers.IO) { ProjectStore.save(context, state) }
        }
    }

    // Leaving the editor: stop playback, cancel export, save once more
    DisposableEffect(Unit) {
        onDispose {
            TimelineModel.setPlaying(false)
            exporter.cancel()
            if (loaded) {
                ProjectStore.save(
                    context,
                    ProjectState(
                        ProjectStore.currentName,
                        TimelineModel.sources.value,
                        TimelineModel.segments.value,
                        TimelineModel.music.value
                    )
                )
            }
        }
    }

    // Poll export progress while exporting
    LaunchedEffect(exporting) {
        while (exporting) {
            exportProgress = exporter.progress()
            delay(250L)
        }
    }

    // Multiple video import: each file is copied into the project folder first
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        scope.launch {
            for (uri in uris) {
                try {
                    val local = withContext(Dispatchers.IO) { ProjectStore.importCopy(context, uri, "video") }
                    val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, local) }
                    TimelineModel.addSource(local, duration)
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val launchVideoPicker: () -> Unit = {
        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    // Background music (audio file), also copied into the project folder
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val local = withContext(Dispatchers.IO) { ProjectStore.importCopy(context, uri, "music") }
                    val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, local) }
                    TimelineModel.setMusic(local, duration)
                } catch (e: Exception) {
                    Toast.makeText(context, "Music import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                    flipV = currentProj.isFlippedV
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

    val extractAudio: () -> Unit = {
        val seg = segments.getOrNull(selected)
        if (seg != null && !extracting) {
            extracting = true
            exporter.extractAudio(
                sources = sources,
                seg = seg,
                onDone = { uri, duration ->
                    extracting = false
                    TimelineModel.setMusic(uri, duration)
                    TimelineModel.setActiveTrack(ActiveTrack.MUSIC)
                    Toast.makeText(context, "Audio extracted to the music track", Toast.LENGTH_SHORT).show()
                },
                onError = { message ->
                    extracting = false
                    Toast.makeText(context, "Extract failed: $message", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val total = segments.sumOf { it.durationMs }
    val hasSelection = selected >= 0
    val hasVideo = segments.isNotEmpty()
    val musicTrack = music
    val musicMode = activeTrack == ActiveTrack.MUSIC && musicTrack != null
    val videoTools = activeTrack == ActiveTrack.VIDEO
    val selectedClip = segments.getOrNull(selected)
    val scrollFrac = if (timelineScroll.maxValue > 0) {
        timelineScroll.value.toFloat() / timelineScroll.maxValue.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .statusBarsPadding()
    ) {
        // --- HEADER ---
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
                text = when {
                    musicMode -> "Music"
                    hasSelection -> "Clip ${selected + 1}"
                    else -> "No clip"
                },
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
        }

        // --- ZOOM CONTROLS (buttons; pinch on the timeline also works) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(MIN_ZOOM) }) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = Color.White)
            }
            Text(text = "${"%.1f".format(zoom)}x", fontSize = 11.sp, color = Color.White)
            IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(MAX_ZOOM) }) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = Color.White)
            }
            Slider(
                value = scrollFrac,
                onValueChange = { v -> scope.launch { timelineScroll.scrollTo((v * timelineScroll.maxValue).toInt()) } },
                enabled = zoom > 1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF5238),
                    activeTrackColor = Color(0xFFFF5238)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }

        // --- TIMELINE (video + music), pinch to zoom, scrolls when zoomed ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .pointerInput(Unit) {
                    // Two-finger pinch changes the zoom. One finger is left to the tracks (seek / move / trim).
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        var lastDistance = 0f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                if (lastDistance > 0f && distance > 0f) {
                                    val factor = distance / lastDistance
                                    zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                }
                                lastDistance = distance
                                event.changes.forEach { it.consume() }
                            } else {
                                lastDistance = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val contentWidth = maxWidth * zoom
            Column(modifier = Modifier.horizontalScroll(timelineScroll)) {
                TimelineView(
                    segments = segments,
                    selectedIndex = selected,
                    playheadMs = playhead,
                    totalMs = total,
                    onTimeChosen = { t ->
                        TimelineModel.requestSeek(t)
                        TimelineModel.selectAtTime(t)
                    },
                    modifier = Modifier.width(contentWidth)
                )
                if (musicTrack != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MusicTrackView(
                        music = musicTrack,
                        totalMs = total,
                        selected = activeTrack == ActiveTrack.MUSIC,
                        onGrab = { TimelineModel.setActiveTrack(ActiveTrack.MUSIC) },
                        onMoveBy = { delta -> TimelineModel.moveMusicBy(delta) },
                        onTrimStartBy = { delta -> TimelineModel.trimMusicStartBy(delta) },
                        onTrimEndBy = { delta -> TimelineModel.trimMusicEndBy(delta) },
                        modifier = Modifier.width(contentWidth)
                    )
                }
            }
        }

        // --- VOLUME BAR (only after the Volume button is pressed) ---
        if (showVolumeBar) {
            if (musicMode && musicTrack != null) {
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
            } else if (selectedClip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        TimelineModel.setSegmentVolume(selected, if (selectedClip.volume > 0f) 0f else 1f)
                    }) {
                        Icon(
                            imageVector = if (selectedClip.volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Clip sound",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Clip sound", fontSize = 11.sp, color = Color.White)
                    Slider(
                        value = selectedClip.volume,
                        onValueChange = { TimelineModel.setSegmentVolume(selected, it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF5238),
                            activeTrackColor = Color(0xFFFF5238)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("${(selectedClip.volume * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFA0A0B2))
                }
            }
        }

        // Add music when there is none
        if (musicTrack == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = launchMusicPicker, enabled = hasVideo) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Music", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- TOOLS (depend on the selected track) ---
        if (musicMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton("Volume", Icons.Default.VolumeUp, enabled = true) {
                    showVolumeBar = !showVolumeBar
                }
                ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                    TimelineModel.trimMusicStartToPlayhead()
                }
                ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                    TimelineModel.trimMusicEndToPlayhead()
                }
                ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                    TimelineModel.removeMusic()
                }
                ToolButton("Back to video", Icons.Default.VideoLibrary, enabled = true) {
                    TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                }
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ToolButton("Volume", Icons.Default.VolumeUp, enabled = hasSelection && videoTools) {
                        showVolumeBar = !showVolumeBar
                    }
                }
                item {
                    ToolButton("Split", Icons.Default.CallSplit, enabled = hasVideo && videoTools) {
                        TimelineModel.splitAtPlayhead()
                    }
                }
                item {
                    ToolButton("Trim start", Icons.Default.FirstPage, enabled = hasSelection && videoTools) {
                        TimelineModel.trimStartToPlayhead()
                    }
                }
                item {
                    ToolButton("Trim end", Icons.Default.LastPage, enabled = hasSelection && videoTools) {
                        TimelineModel.trimEndToPlayhead()
                    }
                }
                item {
                    ToolButton("Copy", Icons.Default.ContentCopy, enabled = hasSelection && videoTools) {
                        TimelineModel.copySelected()
                    }
                }
                item {
                    ToolButton("Cut", Icons.Default.ContentCut, enabled = hasSelection && videoTools) {
                        TimelineModel.cutSelected()
                    }
                }
                item {
                    ToolButton("Paste", Icons.Default.ContentPaste, enabled = hasVideo && videoTools) {
                        TimelineModel.pasteAtPlayhead()
                    }
                }
                item {
                    ToolButton("Delete", Icons.Default.Delete, enabled = hasSelection && videoTools) {
                        TimelineModel.deleteSelected()
                    }
                }
                item {
                    ToolButton("Extract audio", Icons.Default.MusicNote, enabled = hasSelection && videoTools && !extracting) {
                        extractAudio()
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
