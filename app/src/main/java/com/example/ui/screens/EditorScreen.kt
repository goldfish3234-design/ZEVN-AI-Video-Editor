package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.media.ColorPreview
import com.example.ui.media.ExportOptions
import com.example.ui.media.MediaProbe
import com.example.ui.media.ProjectState
import com.example.ui.media.ProjectStore
import com.example.ui.media.TimelineExporter
import com.example.ui.media.TimelineModel
import com.example.ui.media.VideoAdjustPanel
import com.example.ui.media.VideoAdjustStore
import com.example.ui.sticker.StickerLayer
import com.example.ui.sticker.StickerLibraryDialog
import com.example.ui.sticker.StickerStore
import com.example.ui.sticker.StickerTrack
import com.example.ui.sticker.StickerTrackBar
import com.example.ui.text.DraggableTextLayer
import com.example.ui.text.TextEditorDialog
import com.example.ui.text.TextTrack
import com.example.ui.text.TextTrackBar
import com.example.ui.text.textTrimEndToPlayhead
import com.example.ui.text.textTrimStartToPlayhead
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
private const val STICKER_MS = 3000L

private val ActiveBorder = Color(0xFFFF5238)
private val InactiveBorder = Color(0xFF2A2A38)
private val TextBorder = Color(0xFFFFB800)
private val StickerBorder = Color(0xFF7CE38B)

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
    val audioClips by TimelineModel.audio.collectAsState()
    val selectedAudio by TimelineModel.selectedAudio.collectAsState()
    val texts by TextTrack.texts.collectAsState()
    val selectedText by TextTrack.selectedText.collectAsState()
    val stickers by StickerTrack.stickers.collectAsState()
    val selectedSticker by StickerTrack.selectedSticker.collectAsState()
    val adjust by VideoAdjustStore.adjust.collectAsState()

    val currentProj = project ?: return

    val exporter = remember { TimelineExporter(context) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var extracting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    // The volume bar only appears after the Volume tool is pressed
    var showVolumeBar by rememberSaveable { mutableStateOf(false) }
    // The speed choices only appear after the Speed tool is pressed
    var showSpeedBar by rememberSaveable { mutableStateOf(false) }
    // The sticker size bar only appears after the Size tool is pressed
    var showStickerSize by rememberSaveable { mutableStateOf(false) }
    // The adjust panel (filters, effects, sliders) and its live preview appear after the Adjust tool is pressed
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    var showStickerLibrary by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textEditIndex by remember { mutableIntStateOf(-1) }
    val timelineScroll = rememberScrollState()

    // Only one of text / sticker is selected at a time
    LaunchedEffect(selectedText) {
        if (selectedText >= 0) StickerTrack.select(-1)
    }
    LaunchedEffect(selectedSticker) {
        if (selectedSticker >= 0) TextTrack.select(-1)
    }

    // Open the current project once: timeline (if empty), text, stickers and colour settings
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { ProjectStore.loadCurrent(context) }
        if (TimelineModel.sources.value.isEmpty() && saved != null) {
            TimelineModel.restore(saved.sources, saved.segments, saved.music)
        }
        if (saved != null) TextTrack.restore(saved.texts)
        val savedStickers = withContext(Dispatchers.IO) { StickerStore.load(context) }
        StickerTrack.restore(savedStickers)
        withContext(Dispatchers.IO) { VideoAdjustStore.load(context) }
        loaded = true
    }

    // Auto-save shortly after every change
    LaunchedEffect(sources, segments, audioClips, texts, stickers, adjust, loaded) {
        if (loaded) {
            delay(500L)
            val state = ProjectState(ProjectStore.currentName, sources, segments, audioClips, texts)
            withContext(Dispatchers.IO) {
                ProjectStore.save(context, state)
                StickerStore.save(context, stickers)
                VideoAdjustStore.save(context)
            }
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
                        TimelineModel.audio.value,
                        TextTrack.texts.value
                    )
                )
                StickerStore.save(context, StickerTrack.stickers.value)
                VideoAdjustStore.save(context)
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

    // Audio file: copied into the project folder, then added to the audio track at the playhead
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val local = withContext(Dispatchers.IO) { ProjectStore.importCopy(context, uri, "music") }
                    val duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, local) }
                    TimelineModel.addAudio(local, duration)
                } catch (e: Exception) {
                    Toast.makeText(context, "Audio import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val launchMusicPicker: () -> Unit = {
        musicPicker.launch(arrayOf("audio/*"))
    }

    // Image from the phone: copied into the project folder, then placed on the preview at the playhead
    val stickerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val local = withContext(Dispatchers.IO) { ProjectStore.importCopy(context, uri, "sticker") }
                    StickerTrack.add(local.toString(), TimelineModel.playheadMs.value, STICKER_MS)
                } catch (e: Exception) {
                    Toast.makeText(context, "Image import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val launchStickerPicker: () -> Unit = {
        stickerPicker.launch(arrayOf("image/*"))
    }

    val startExport: () -> Unit = {
        if (segments.isNotEmpty() && !exporting) {
            exporting = true
            exportProgress = 0
            exporter.export(
                sources = sources,
                segments = segments,
                music = audioClips,
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
            // the extracted audio starts where the selected clip starts on the timeline
            var clipStart = 0L
            for (k in 0 until selected) clipStart += segments[k].durationMs
            extracting = true
            exporter.extractAudio(
                sources = sources,
                seg = seg,
                onDone = { uri, duration ->
                    extracting = false
                    TimelineModel.addAudio(uri, duration, clipStart)
                    Toast.makeText(context, "Audio extracted to the audio track", Toast.LENGTH_SHORT).show()
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
    val audioSel = audioClips.getOrNull(selectedAudio)
    val textSel = texts.getOrNull(selectedText)
    val stickerSel = stickers.getOrNull(selectedSticker)
    val textMode = textSel != null
    val stickerMode = !textMode && stickerSel != null
    val audioMode = !textMode && !stickerMode && activeTrack == ActiveTrack.MUSIC && audioSel != null
    val videoTools = !textMode && !stickerMode && activeTrack == ActiveTrack.VIDEO
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

        // --- PREVIEW: the frame keeps the chosen social media shape and fits on screen ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val ratio = currentProj.aspectRatio.ratio
                // the largest frame with this ratio that fits in the free space
                val frameW = minOf(maxWidth, maxHeight * ratio)
                val frameH = frameW / ratio
                Box(
                    modifier = Modifier
                        .width(frameW)
                        .height(frameH)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                        .clickable(enabled = hasVideo) { TimelineModel.togglePlaying() }
                ) {
                    val mediaModifier = Modifier
                        .fillMaxSize()
                        .rotate(currentProj.rotationAngle.toFloat())

                    if (hasVideo) {
                        VideoPreview(sources = sources, segments = segments, modifier = mediaModifier)
                        // While the Adjust panel is open, the frame under the playhead shows the colours live
                        if (showAdjust && videoTools) {
                            ColorPreview(
                                sources = sources,
                                segments = segments,
                                playheadMs = playhead,
                                adjust = adjust,
                                modifier = mediaModifier
                            )
                        }
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

                    // Stickers and text, drawn on top of the video. Drag them to move.
                    StickerLayer(
                        stickers = stickers,
                        playheadMs = playhead,
                        selectedIndex = selectedSticker,
                        modifier = Modifier.fillMaxSize()
                    )
                    DraggableTextLayer(
                        texts = texts,
                        playheadMs = playhead,
                        selectedIndex = selectedText,
                        modifier = Modifier.fillMaxSize()
                    )
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
                    textMode -> "Text ${selectedText + 1}"
                    stickerMode -> "Sticker ${selectedSticker + 1}"
                    audioMode -> "Audio ${selectedAudio + 1}"
                    hasSelection && selectedClip != null && abs(selectedClip.speed - 1f) > 0.01f ->
                        "Clip ${selected + 1} · ${formatSpeed(selectedClip.speed)}"
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

        // --- TIMELINE (video + audio + text + stickers), pinch to zoom, scrolls when zoomed ---
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
            val playFrac = if (total > 0L) (playhead.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

            Box(modifier = Modifier.horizontalScroll(timelineScroll)) {
                Column(modifier = Modifier.width(contentWidth)) {
                    // The track being edited has a bright border
                    TimelineView(
                        segments = segments,
                        selectedIndex = selected,
                        playheadMs = playhead,
                        totalMs = total,
                        onTimeChosen = { t ->
                            TextTrack.select(-1)
                            StickerTrack.select(-1)
                            TimelineModel.requestSeek(t)
                            TimelineModel.selectAtTime(t)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 2.dp,
                                color = if (videoTools) ActiveBorder else InactiveBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    if (audioClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MusicTrackView(
                            clips = audioClips,
                            totalMs = total,
                            selectedIndex = selectedAudio,
                            onSelect = { i ->
                                TextTrack.select(-1)
                                StickerTrack.select(-1)
                                TimelineModel.selectAudio(i)
                            },
                            onMoveBy = { i, delta -> TimelineModel.moveAudioBy(i, delta) },
                            onTrimStartBy = { i, delta -> TimelineModel.trimAudioStartBy(i, delta) },
                            onTrimEndBy = { i, delta -> TimelineModel.trimAudioEndBy(i, delta) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 2.dp,
                                    color = if (audioMode) ActiveBorder else InactiveBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                    }
                    if (texts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextTrackBar(
                            texts = texts,
                            totalMs = total,
                            selectedIndex = selectedText,
                            onSelect = { i -> TextTrack.select(i) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 2.dp,
                                    color = if (textMode) TextBorder else InactiveBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                    }
                    if (stickers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StickerTrackBar(
                            stickers = stickers,
                            totalMs = total,
                            selectedIndex = selectedSticker,
                            onSelect = { i -> StickerTrack.select(i) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 2.dp,
                                    color = if (stickerMode) StickerBorder else InactiveBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                    }
                }

                // One playhead line across ALL tracks, with a handle on top
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .offset(x = contentWidth * playFrac - (3.dp / 2))
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = contentWidth * playFrac - 8.dp, y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // --- STICKER SIZE (only after the Size tool is pressed) ---
        if (showStickerSize && stickerMode && stickerSel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sticker size", fontSize = 11.sp, color = Color.White)
                Slider(
                    value = stickerSel.sizeFrac,
                    onValueChange = { StickerTrack.setSize(selectedSticker, it) },
                    valueRange = 0.05f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7CE38B),
                        activeTrackColor = Color(0xFF7CE38B)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("${(stickerSel.sizeFrac * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFA0A0B2))
            }
        }

        // --- SPEED CHOICES (only after the Speed tool is pressed) ---
        if (showSpeedBar && selectedClip != null && videoTools) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Speed", fontSize = 11.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        TimelineModel.SPEED_CHOICES.forEach { choice ->
                            val chosen = abs(selectedClip.speed - choice) < 0.01f
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (chosen) Color(0xFFFF5238) else Color(0xFF222230))
                                    .clickable { TimelineModel.setSegmentSpeed(selected, choice) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    formatSpeed(choice),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- VOLUME BAR (only after the Volume button is pressed) ---
        if (showVolumeBar) {
            if (audioMode && audioSel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio", fontSize = 11.sp, color = Color.White)
                    Slider(
                        value = audioSel.volume,
                        onValueChange = { TimelineModel.setAudioVolume(selectedAudio, it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF5238),
                            activeTrackColor = Color(0xFFFF5238)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("${(audioSel.volume * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFA0A0B2))
                }
            } else if (selectedClip != null && videoTools) {
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

        // --- VIDEO ADJUST PANEL (only after the Adjust tool is pressed) ---
        if (showAdjust && hasVideo && videoTools) {
            VideoAdjustPanel(modifier = Modifier.padding(bottom = 4.dp))
        }

        // Add audio, text and sticker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = launchMusicPicker, enabled = hasVideo) {
                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Audio", fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                textEditIndex = -1
                showTextDialog = true
            }, enabled = hasVideo) {
                Icon(Icons.Default.TextFields, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Text", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { showStickerLibrary = true }, enabled = hasVideo) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sticker", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- TOOLS (depend on the selected track) ---
        if (textMode) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ToolButton("Edit text", Icons.Default.Edit, enabled = true) {
                        textEditIndex = selectedText
                        showTextDialog = true
                    }
                }
                item {
                    ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                        textSel?.let { TextTrack.splitById(it.id, playhead) }
                    }
                }
                item {
                    ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                        textTrimStartToPlayhead(selectedText, playhead)
                    }
                }
                item {
                    ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                        textTrimEndToPlayhead(selectedText, playhead)
                    }
                }
                item {
                    ToolButton("Duplicate", Icons.Default.ContentCopy, enabled = true) {
                        TextTrack.duplicate(selectedText)
                    }
                }
                item {
                    ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                        TextTrack.delete(selectedText)
                    }
                }
                item {
                    ToolButton("Back to video", Icons.Default.VideoLibrary, enabled = true) {
                        TextTrack.select(-1)
                        TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                    }
                }
            }
        } else if (stickerMode) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ToolButton("Size", Icons.Default.Straighten, enabled = true) {
                        showStickerSize = !showStickerSize
                    }
                }
                item {
                    ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                        StickerTrack.trimStartToPlayhead(selectedSticker, playhead)
                    }
                }
                item {
                    ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                        StickerTrack.trimEndToPlayhead(selectedSticker, playhead)
                    }
                }
                item {
                    ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                        StickerTrack.delete(selectedSticker)
                    }
                }
                item {
                    ToolButton("Back to video", Icons.Default.VideoLibrary, enabled = true) {
                        StickerTrack.select(-1)
                        TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                    }
                }
            }
        } else if (audioMode) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ToolButton("Volume", Icons.Default.VolumeUp, enabled = true) {
                        showVolumeBar = !showVolumeBar
                    }
                }
                item {
                    ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                        TimelineModel.splitAudioAtPlayhead()
                    }
                }
                item {
                    ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                        TimelineModel.trimAudioStartToPlayhead(selectedAudio)
                    }
                }
                item {
                    ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                        TimelineModel.trimAudioEndToPlayhead(selectedAudio)
                    }
                }
                item {
                    ToolButton("Copy", Icons.Default.ContentCopy, enabled = true) {
                        TimelineModel.copyAudio(selectedAudio)
                    }
                }
                item {
                    ToolButton("Cut", Icons.Default.ContentCut, enabled = true) {
                        TimelineModel.cutAudio(selectedAudio)
                    }
                }
                item {
                    ToolButton("Paste", Icons.Default.ContentPaste, enabled = true) {
                        TimelineModel.pasteAudioAtPlayhead()
                    }
                }
                item {
                    ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                        TimelineModel.deleteAudio(selectedAudio)
                    }
                }
                item {
                    ToolButton("Back to video", Icons.Default.VideoLibrary, enabled = true) {
                        TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                    }
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
                    ToolButton("Adjust", Icons.Default.Tune, enabled = hasVideo && videoTools) {
                        showAdjust = !showAdjust
                    }
                }
                item {
                    ToolButton("Speed", Icons.Default.Speed, enabled = hasSelection && videoTools) {
                        showSpeedBar = !showSpeedBar
                    }
                }
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

    // --- STICKER LIBRARY (built-in stickers, or import from the phone) ---
    if (showStickerLibrary) {
        StickerLibraryDialog(
            onPick = { uri ->
                StickerTrack.add(uri, TimelineModel.playheadMs.value, STICKER_MS)
                showStickerLibrary = false
            },
            onImport = {
                showStickerLibrary = false
                launchStickerPicker()
            },
            onDismiss = { showStickerLibrary = false }
        )
    }

    // --- TEXT EDITOR ---
    if (showTextDialog) {
        val editing = if (textEditIndex >= 0) texts.getOrNull(textEditIndex) else null
        TextEditorDialog(
            initial = editing,
            defaultStartMs = playhead,
            onDismiss = { showTextDialog = false },
            onSave = { clip ->
                if (editing == null) {
                    TextTrack.add(clip.copy(id = TextTrack.newId()))
                } else {
                    TextTrack.replace(textEditIndex, clip)
                }
                showTextDialog = false
            }
        )
    }

    // --- EXPORT PROGRESS DIALOG ---
    if (exporting) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF161622),
            title = { Text("Exporting video", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Rendering video, audio, text and stickers...", fontSize = 12.sp, color = Color(0xFFA0A0B2))
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

private fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${text}x"
}
