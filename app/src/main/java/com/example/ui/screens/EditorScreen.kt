package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MusicTrackView
import com.example.ui.components.TimelineView
import com.example.ui.components.VideoPreview
import com.example.ui.media.ActiveTrack
import com.example.ui.media.AutoCut
import com.example.ui.media.ColorPreview
import com.example.ui.media.ExportOptions
import com.example.ui.media.LiveCutout
import com.example.ui.media.MediaProbe
import com.example.ui.media.ProjectState
import com.example.ui.media.ProjectStore
import com.example.ui.media.SilenceRange
import com.example.ui.media.TimelineExporter
import com.example.ui.media.TimelineModel
import com.example.ui.media.TransitionPreview
import com.example.ui.media.TransitionStore
import com.example.ui.media.VideoAdjustPanel
import com.example.ui.media.VideoAdjustStore
import com.example.ui.media.VideoEffectsPanel
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
// Leaving the editor needs a second press within this time
private const val EXIT_WINDOW_MS = 2000L

// Track row heights (the same heights are used by the labels on the left)
private val VideoRowH = 56.dp
private val AudioRowH = 36.dp
private val TextRowH = 40.dp
private val OverlayRowH = 36.dp
private val RowGap = 6.dp
private val LabelW = 64.dp

private val ActiveBorder = Color(0xFFFF5238)
private val InactiveBorder = Color(0xFF2A2A38)
private val TextBorder = Color(0xFFFFB800)
private val StickerBorder = Color(0xFF7CE38B)
private val BarBg = Color(0xFF111116)
private val LabelBg = Color(0xFF1B1B25)
private val Muted = Color(0xFF8A8AA0)

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
    val transitionMap by TransitionStore.map.collectAsState()

    val currentProj = project ?: return

    val exporter = remember { TimelineExporter(context) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var extracting by remember { mutableStateOf(false) }
    var autoCutBusy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    // Which category the tool strip shows: edit, audio, text, effects, overlay, filter
    var tab by rememberSaveable { mutableStateOf("edit") }
    // Panels and toggles (only one colour panel at a time)
    var showVolumeBar by rememberSaveable { mutableStateOf(false) }
    var showSpeedBar by rememberSaveable { mutableStateOf(false) }
    var showStickerSize by rememberSaveable { mutableStateOf(false) }
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    var showEffects by rememberSaveable { mutableStateOf(false) }
    var showLiveCutout by rememberSaveable { mutableStateOf(false) }
    var showTransitions by rememberSaveable { mutableStateOf(false) }
    var showStickerLibrary by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textEditIndex by remember { mutableIntStateOf(-1) }
    // Time of the last press that tried to leave the editor
    var lastExitPress by remember { mutableLongStateOf(0L) }
    val timelineScroll = rememberScrollState()

    // Leaving the editor needs two presses: the first one only shows a hint
    val onExit: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastExitPress < EXIT_WINDOW_MS) {
            onNavigateBack()
        } else {
            lastExitPress = now
            Toast.makeText(context, "আবার চাপলে এডিটর থেকে বের হবে", Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler { onExit() }

    // Only one of text / sticker is selected at a time
    LaunchedEffect(selectedText) {
        if (selectedText >= 0) {
            StickerTrack.select(-1)
            tab = "text"
        }
    }
    LaunchedEffect(selectedSticker) {
        if (selectedSticker >= 0) {
            TextTrack.select(-1)
            tab = "overlay"
        }
    }
    LaunchedEffect(selectedAudio) {
        if (selectedAudio >= 0) tab = "audio"
    }

    // Open the current project once: timeline (if empty), text, stickers, colour and transitions
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { ProjectStore.loadCurrent(context) }
        if (TimelineModel.sources.value.isEmpty() && saved != null) {
            TimelineModel.restore(saved.sources, saved.segments, saved.music)
        }
        if (saved != null) TextTrack.restore(saved.texts)
        val savedStickers = withContext(Dispatchers.IO) { StickerStore.load(context) }
        StickerTrack.restore(savedStickers)
        withContext(Dispatchers.IO) { VideoAdjustStore.load(context) }
        withContext(Dispatchers.IO) { TransitionStore.load(context) }
        loaded = true
    }

    // Auto-save shortly after every change
    LaunchedEffect(sources, segments, audioClips, texts, stickers, adjust, transitionMap, loaded) {
        if (loaded) {
            delay(500L)
            val state = ProjectState(ProjectStore.currentName, sources, segments, audioClips, texts)
            withContext(Dispatchers.IO) {
                ProjectStore.save(context, state)
                StickerStore.save(context, stickers)
                VideoAdjustStore.save(context)
                TransitionStore.save(context)
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
                TransitionStore.save(context)
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

    // AutoCut: finds the quiet parts of every video clip and cuts them out (undo brings them back)
    val runAutoCut: () -> Unit = {
        if (segments.isNotEmpty() && !autoCutBusy) {
            autoCutBusy = true
            Toast.makeText(context, "AutoCut চলছে...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val before = TimelineModel.totalDurationMs
                val result = withContext(Dispatchers.IO) {
                    val segs = TimelineModel.segments.value
                    val srcs = TimelineModel.sources.value
                    val map = HashMap<Int, List<SilenceRange>>()
                    for (i in segs.indices) {
                        val s = segs[i]
                        val src = srcs.getOrNull(s.source) ?: continue
                        map[i] = AutoCut.findSilence(context, src.uri, s.startMs, s.endMs)
                    }
                    AutoCut.cutSegments(segs, map)
                }
                autoCutBusy = false
                val after = result.sumOf { it.durationMs }
                if (after >= before - 50L) {
                    Toast.makeText(context, "কোনো নীরব অংশ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                } else {
                    TimelineModel.applyAutoCut(result)
                    val removedSec = (before - after) / 1000.0
                    Toast.makeText(
                        context,
                        "AutoCut: %.1f সেকেন্ড নীরব অংশ বাদ দেওয়া হয়েছে".format(removedSec),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
    // In colour mode the screen is used for the picture: the timeline is replaced by a simple scrub bar
    val colourPanelOpen = (showAdjust || showEffects) && hasVideo && videoTools
    val selectedClip = segments.getOrNull(selected)
    // the transition type of the selected clip (recomputed when the transitions change)
    val currentTransition = if (selectedClip != null) {
        transitionMap[TransitionStore.keyOf(selectedClip)] ?: "none"
    } else {
        "none"
    }
    val scrollFrac = if (timelineScroll.maxValue > 0) {
        timelineScroll.value.toFloat() / timelineScroll.maxValue.toFloat()
    } else {
        0f
    }
    val categories = listOf(
        Triple("edit", "Edit", Icons.Default.ContentCut),
        Triple("audio", "Audio", Icons.Default.MusicNote),
        Triple("text", "Text", Icons.Default.TextFields),
        Triple("effects", "Effects", Icons.Default.Palette),
        Triple("overlay", "Overlay", Icons.Default.Image),
        Triple("filter", "Filter", Icons.Default.Tune)
    )

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
            IconButton(onClick = onExit) {
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

        // --- PREVIEW: the largest frame with the chosen social media shape, centred in the free space ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val ratio = currentProj.aspectRatio.ratio
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
                        if (colourPanelOpen) {
                            ColorPreview(
                                sources = sources,
                                segments = segments,
                                playheadMs = playhead,
                                adjust = adjust,
                                modifier = mediaModifier
                            )
                        }
                        if (showLiveCutout) {
                            LiveCutout(
                                sources = sources,
                                segments = segments,
                                playheadMs = playhead,
                                modifier = mediaModifier
                            )
                        }
                        TransitionPreview(
                            sources = sources,
                            segments = segments,
                            playheadMs = playhead,
                            modifier = mediaModifier
                        )
                    } else {
                        // No video yet: an empty black frame with an import button (no default picture)
                        Button(
                            onClick = launchVideoPicker,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.align(Alignment.Center)
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
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatMs(playhead)} / ${formatMs(total)}",
                fontSize = 12.sp,
                color = Muted
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    stickerMode -> "Overlay ${selectedSticker + 1}"
                    audioMode -> "Audio ${selectedAudio + 1}"
                    colourPanelOpen -> "Colour"
                    showLiveCutout -> "Remove BG"
                    hasSelection && selectedClip != null && abs(selectedClip.speed - 1f) > 0.01f ->
                        "Clip ${selected + 1} · ${formatSpeed(selectedClip.speed)}"
                    hasSelection -> "Clip ${selected + 1}"
                    else -> "No clip"
                },
                fontSize = 12.sp,
                color = Muted
            )
        }

        if (colourPanelOpen) {
            // --- COLOUR MODE: a simple scrub bar, so the picture gets the space ---
            Slider(
                value = if (total > 0L) (playhead.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { v -> TimelineModel.requestSeek((v * total).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF5238),
                    activeTrackColor = Color(0xFFFF5238)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        } else {
            // --- ZOOM CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
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

            // --- TRACKS: labels on the left, the timeline on the right (pinch to zoom, scrolls when zoomed) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left: one label per track. A track row only exists when it has content (except video).
                Column(modifier = Modifier.width(LabelW)) {
                    TrackLabel(
                        height = VideoRowH,
                        icon = if (selectedClip != null && selectedClip.volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        text = "Mute clip",
                        tint = Color.White,
                        onClick = {
                            if (selectedClip != null) {
                                TimelineModel.setSegmentVolume(selected, if (selectedClip.volume > 0f) 0f else 1f)
                            }
                        }
                    )
                    if (audioClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(RowGap))
                        TrackLabel(AudioRowH, Icons.Default.MusicNote, "Audio", Color(0xFFFF7A5C)) { tab = "audio" }
                    }
                    if (texts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(RowGap))
                        TrackLabel(TextRowH, Icons.Default.TextFields, "Text", TextBorder) { tab = "text" }
                    }
                    if (stickers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(RowGap))
                        TrackLabel(OverlayRowH, Icons.Default.Image, "Overlay", StickerBorder) { tab = "overlay" }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
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
                            Box(modifier = Modifier.height(VideoRowH)) {
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
                            }
                            if (audioClips.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(RowGap))
                                Box(modifier = Modifier.height(AudioRowH)) {
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
                            }
                            if (texts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(RowGap))
                                Box(modifier = Modifier.height(TextRowH)) {
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
                            }
                            if (stickers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(RowGap))
                                Box(modifier = Modifier.height(OverlayRowH)) {
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
            }
        }

        // --- PANELS (open only when their tool is pressed) ---
        if (showStickerSize && stickerMode && stickerSel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Size", fontSize = 11.sp, color = Color.White)
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
                Text("${(stickerSel.sizeFrac * 100).toInt()}%", fontSize = 11.sp, color = Muted)
            }
        }

        if (showTransitions && selectedClip != null && videoTools) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransitionStore.TYPES.forEach { (key, label) ->
                        val chosen = currentTransition == key
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (chosen) Color(0xFFFF5238) else Color(0xFF222230))
                                .clickable { TransitionStore.set(selectedClip, key) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    if (selected == 0) "The first clip has nothing before it, so its transition is not shown"
                    else "The transition plays at the start of this clip (0.5 s)",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }

        if (showSpeedBar && selectedClip != null && videoTools) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        Text(formatSpeed(choice), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showVolumeBar) {
            if (audioMode && audioSel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
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
                    Text("${(audioSel.volume * 100).toInt()}%", fontSize = 11.sp, color = Muted)
                }
            } else if (selectedClip != null && videoTools) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Text("${(selectedClip.volume * 100).toInt()}%", fontSize = 11.sp, color = Muted)
                }
            }
        }

        if (showAdjust && colourPanelOpen) {
            VideoAdjustPanel(modifier = Modifier.padding(vertical = 2.dp))
        }
        if (showEffects && colourPanelOpen) {
            VideoEffectsPanel(modifier = Modifier.padding(vertical = 2.dp))
        }

        // --- TOOL STRIP: the tools of the chosen category, in one scrolling row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (tab) {
                "edit" -> when {
                    textMode -> {
                        ToolButton("Edit text", Icons.Default.Edit, enabled = true) {
                            textEditIndex = selectedText
                            showTextDialog = true
                        }
                        ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                            textSel?.let { TextTrack.splitById(it.id, playhead) }
                        }
                        ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                            textTrimStartToPlayhead(selectedText, playhead)
                        }
                        ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                            textTrimEndToPlayhead(selectedText, playhead)
                        }
                        ToolButton("Duplicate", Icons.Default.ContentCopy, enabled = true) {
                            TextTrack.duplicate(selectedText)
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            TextTrack.delete(selectedText)
                        }
                        ToolButton("Back", Icons.Default.VideoLibrary, enabled = true) {
                            TextTrack.select(-1)
                            TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                        }
                    }
                    stickerMode -> {
                        ToolButton("Size", Icons.Default.Straighten, enabled = true, active = showStickerSize) {
                            showStickerSize = !showStickerSize
                        }
                        ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                            StickerTrack.trimStartToPlayhead(selectedSticker, playhead)
                        }
                        ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                            StickerTrack.trimEndToPlayhead(selectedSticker, playhead)
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            StickerTrack.delete(selectedSticker)
                        }
                        ToolButton("Back", Icons.Default.VideoLibrary, enabled = true) {
                            StickerTrack.select(-1)
                            TimelineModel.setActiveTrack(ActiveTrack.VIDEO)
                        }
                    }
                    audioMode -> {
                        ToolButton("Volume", Icons.Default.VolumeUp, enabled = true, active = showVolumeBar) {
                            showVolumeBar = !showVolumeBar
                        }
                        ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                            TimelineModel.splitAudioAtPlayhead()
                        }
                        ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                            TimelineModel.trimAudioStartToPlayhead(selectedAudio)
                        }
                        ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                            TimelineModel.trimAudioEndToPlayhead(selectedAudio)
                        }
                        ToolButton("Copy", Icons.Default.ContentCopy, enabled = true) {
                            TimelineModel.copyAudio(selectedAudio)
                        }
                        ToolButton("Cut", Icons.Default.ContentCut, enabled = true) {
                            TimelineModel.cutAudio(selectedAudio)
                        }
                        ToolButton("Paste", Icons.Default.ContentPaste, enabled = true) {
                            TimelineModel.pasteAudioAtPlayhead()
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            TimelineModel.deleteAudio(selectedAudio)
                        }
                    }
                    else -> {
                        ToolButton("AutoCut", Icons.Default.AutoAwesome, enabled = hasVideo && videoTools && !autoCutBusy) {
                            runAutoCut()
                        }
                        ToolButton("Split", Icons.Default.CallSplit, enabled = hasVideo && videoTools) {
                            TimelineModel.splitAtPlayhead()
                        }
                        ToolButton("Trim start", Icons.Default.FirstPage, enabled = hasSelection && videoTools) {
                            TimelineModel.trimStartToPlayhead()
                        }
                        ToolButton("Trim end", Icons.Default.LastPage, enabled = hasSelection && videoTools) {
                            TimelineModel.trimEndToPlayhead()
                        }
                        ToolButton("Copy", Icons.Default.ContentCopy, enabled = hasSelection && videoTools) {
                            TimelineModel.copySelected()
                        }
                        ToolButton("Cut", Icons.Default.ContentCut, enabled = hasSelection && videoTools) {
                            TimelineModel.cutSelected()
                        }
                        ToolButton("Paste", Icons.Default.ContentPaste, enabled = hasVideo && videoTools) {
                            TimelineModel.pasteAtPlayhead()
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = hasSelection && videoTools) {
                            TimelineModel.deleteSelected()
                        }
                        ToolButton("Speed", Icons.Default.Speed, enabled = hasSelection && videoTools, active = showSpeedBar) {
                            showSpeedBar = !showSpeedBar
                        }
                        ToolButton("Volume", Icons.Default.VolumeUp, enabled = hasSelection && videoTools, active = showVolumeBar) {
                            showVolumeBar = !showVolumeBar
                        }
                        ToolButton("Extract audio", Icons.Default.MusicNote, enabled = hasSelection && videoTools && !extracting) {
                            extractAudio()
                        }
                        ToolButton("Rotate", Icons.Default.RotateRight, enabled = true) {
                            viewModel.rotate()
                        }
                    }
                }

                "audio" -> {
                    ToolButton("Add audio", Icons.Default.Add, enabled = hasVideo) {
                        launchMusicPicker()
                    }
                    ToolButton("Extract audio", Icons.Default.MusicNote, enabled = hasSelection && videoTools && !extracting) {
                        extractAudio()
                    }
                    ToolButton("Clip sound", Icons.Default.VolumeUp, enabled = hasSelection && videoTools, active = showVolumeBar) {
                        showVolumeBar = !showVolumeBar
                    }
                    if (audioMode) {
                        ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                            TimelineModel.splitAudioAtPlayhead()
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            TimelineModel.deleteAudio(selectedAudio)
                        }
                    }
                }

                "text" -> {
                    ToolButton("Add text", Icons.Default.TextFields, enabled = hasVideo) {
                        textEditIndex = -1
                        showTextDialog = true
                    }
                    if (textMode) {
                        ToolButton("Edit text", Icons.Default.Edit, enabled = true) {
                            textEditIndex = selectedText
                            showTextDialog = true
                        }
                        ToolButton("Split", Icons.Default.CallSplit, enabled = true) {
                            textSel?.let { TextTrack.splitById(it.id, playhead) }
                        }
                        ToolButton("Duplicate", Icons.Default.ContentCopy, enabled = true) {
                            TextTrack.duplicate(selectedText)
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            TextTrack.delete(selectedText)
                        }
                    }
                }

                "effects" -> {
                    ToolButton("Effects", Icons.Default.Palette, enabled = hasVideo && videoTools, active = showEffects) {
                        showEffects = !showEffects
                        if (showEffects) {
                            showAdjust = false
                            showLiveCutout = false
                            showTransitions = false
                        }
                    }
                    ToolButton("Remove BG", Icons.Default.AutoFixNormal, enabled = hasVideo && videoTools, active = showLiveCutout) {
                        showLiveCutout = !showLiveCutout
                        if (showLiveCutout) {
                            showAdjust = false
                            showEffects = false
                            showTransitions = false
                        }
                    }
                    ToolButton("Transition", Icons.Default.SwapHoriz, enabled = hasSelection && videoTools, active = showTransitions) {
                        showTransitions = !showTransitions
                        if (showTransitions) {
                            showAdjust = false
                            showEffects = false
                            showLiveCutout = false
                        }
                    }
                }

                "overlay" -> {
                    ToolButton("Add sticker", Icons.Default.Image, enabled = hasVideo) {
                        showStickerLibrary = true
                    }
                    if (stickerMode) {
                        ToolButton("Size", Icons.Default.Straighten, enabled = true, active = showStickerSize) {
                            showStickerSize = !showStickerSize
                        }
                        ToolButton("Trim start", Icons.Default.FirstPage, enabled = true) {
                            StickerTrack.trimStartToPlayhead(selectedSticker, playhead)
                        }
                        ToolButton("Trim end", Icons.Default.LastPage, enabled = true) {
                            StickerTrack.trimEndToPlayhead(selectedSticker, playhead)
                        }
                        ToolButton("Delete", Icons.Default.Delete, enabled = true) {
                            StickerTrack.delete(selectedSticker)
                        }
                    }
                }

                "filter" -> {
                    ToolButton("Adjust", Icons.Default.Tune, enabled = hasVideo && videoTools, active = showAdjust) {
                        showAdjust = !showAdjust
                        if (showAdjust) {
                            showEffects = false
                            showLiveCutout = false
                            showTransitions = false
                        }
                    }
                    ToolButton("Reset colour", Icons.Default.Undo, enabled = hasVideo && videoTools) {
                        VideoAdjustStore.reset()
                    }
                }
            }
        }

        // --- CATEGORY BAR (fixed at the bottom, like the editor's main menu) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BarBg)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { (key, label, icon) ->
                val on = tab == key
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { tab = key }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (on) Color.White else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color.White else Muted
                    )
                }
            }
        }
    }

    // --- STICKER LIBRARY (built-in stickers, photos, picture-in-picture) ---
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
                    Text("Rendering video, audio, text and stickers...", fontSize = 12.sp, color = Muted)
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

/** A track label on the left of a track row: an icon and a short name, the same height as the track. */
@Composable
private fun TrackLabel(
    height: Dp,
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(LabelW)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(LabelBg)
            .clickable { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, fontSize = 9.sp, color = Color(0xFFD4D4DE), maxLines = 1)
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> Color(0xFF4A4A58)
        active -> Color(0xFFFF5238)
        else -> Color(0xFFD4D4DE)
    }
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
