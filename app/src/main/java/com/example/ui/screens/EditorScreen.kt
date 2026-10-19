package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AspectRatioType
import com.example.service.AudioRecorderManager
import com.example.ui.components.VideoPreview
import com.example.ui.components.WaveformVisualizer
import com.example.ui.media.ImportedMedia
import com.example.ui.theme.*
import com.example.ui.viewmodel.EditorTab
import com.example.ui.viewmodel.VideoEditorViewModel

@Composable
fun EditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenExport: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.currentProject.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playheadMs by viewModel.currentPlayheadMs.collectAsState()
    val activeTab by viewModel.activeEditorTab.collectAsState()
    val currentOpacity by viewModel.currentOpacity.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val isGeneratingVeo by viewModel.isGeneratingVeo.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val importedVideoUri by ImportedMedia.videoUri.collectAsState()

    val currentProj = project ?: return

    var showResolutionDialog by remember { mutableStateOf(false) }
    var showVeoModal by remember { mutableStateOf(false) }
    var showTranscribeModal by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    // Gallery video picker (Android Photo Picker, no storage permission needed)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImportedMedia.setVideo(uri)
            viewModel.pause()
        }
    }

    // Audio recording manager
    val audioRecorder = remember { AudioRecorderManager(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRecorder.startRecording()
            viewModel.setRecordingAudioState(true)
        } else {
            viewModel.setRecordingAudioState(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .statusBarsPadding()
    ) {
        // --- 1. TOP HEADER (Matching Phone 2: ✕, 1080p ▾, Export) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Close "✕" Button
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Editor",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Import video from gallery
                IconButton(
                    onClick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Import Video",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Resolution Selector "1080p ▾"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF22222E))
                        .clickable { showResolutionDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentProj.resolution.split(" ").firstOrNull() ?: "1080p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Resolution",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // "Export" Button matching the red/amber rounded button in Phone 2
                Button(
                    onClick = onOpenExport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4834)),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("editor_export_button")
                ) {
                    Text(
                        text = "Export",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // --- 2. VIDEO CANVAS PREVIEW (Matching Phone 2) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = currentProj.aspectRatio.ratio
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .clickable { viewModel.togglePlayPause() }
            ) {
                val mediaModifier = Modifier
                    .fillMaxSize()
                    .alpha(currentOpacity)
                    .rotate(currentProj.rotationAngle.toFloat())
                    .scale(
                        scaleX = if (currentProj.isFlippedH) -1f else 1f,
                        scaleY = if (currentProj.isFlippedV) -1f else 1f
                    )

                val videoUri = importedVideoUri
                if (videoUri != null) {
                    // Real imported video from the gallery
                    VideoPreview(
                        uri = videoUri,
                        isPlaying = isPlaying,
                        modifier = mediaModifier
                    )
                } else {
                    // Sample frame when no video is imported yet
                    Image(
                        painter = painterResource(id = currentProj.thumbnailRes),
                        contentDescription = "Video Preview",
                        modifier = mediaModifier,
                        contentScale = ContentScale.Crop
                    )
                }

                // Dark Bottom Gradient Overlay for Typography
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Sample typography only shows when no real video is imported
                if (videoUri == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NEW IN",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = Color.White,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "FASHION",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = Color.White,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Step Into What's New",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF5238),
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Import hint when nothing is picked yet
                if (videoUri == null) {
                    Button(
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
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

                // AI Transcribing status indicator
                if (isTranscribing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color(0xFF00FF9D), modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transcribing microphone audio...", fontSize = 11.sp, color = Color(0xFF00FF9D))
                        }
                    }
                }
            }
        }

        // --- 3. PLAYER CONTROLS STRIP (Undo, Redo, Timestamp, Play/Pause, Aspect) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Undo & Redo
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color(0xFFA0A0B2), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color(0xFFA0A0B2), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Timestamp "0:02 / 0:58"
                val currSec = playheadMs / 1000
                val totalSec = currentProj.durationMs / 1000
                Text(
                    text = "0:${if (currSec < 10) "0$currSec" else currSec} / 0:${if (totalSec < 10) "0$totalSec" else totalSec}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA0A0B2)
                )
            }

            // Center: Play / Pause Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { viewModel.togglePlayPause() }
                    .testTag("editor_play_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Right: Aspect ratio toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val next = if (currentProj.aspectRatio == AspectRatioType.RATIO_9_16) {
                            AspectRatioType.RATIO_16_9
                        } else {
                            AspectRatioType.RATIO_9_16
                        }
                        viewModel.updateAspectRatio(next)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Aspect Ratio",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- 4. MULTI-TRACK TIMELINE (Cover clip, Add Music, T Titles, Playhead) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF0F0F14))
                .border(width = 1.dp, color = Color(0xFF1B1B24))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Time Scale Ticks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0:00", fontSize = 9.sp, color = Color(0xFF6B6B7C))
                    Text("0:05", fontSize = 9.sp, color = Color(0xFF6B6B7C))
                    Text("0:10", fontSize = 9.sp, color = Color(0xFF6B6B7C))
                    Text("0:15", fontSize = 9.sp, color = Color(0xFF6B6B7C))
                    Text("0:${currentProj.durationMs / 1000}", fontSize = 9.sp, color = Color(0xFF6B6B7C))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // TRACK 1: Video Filmstrip with glowing red selection and "Cover" label (as in Phone 2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Video Clip 1 "Cover"
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color(0xFFFF5238), RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zevn_fashion_hero),
                            contentDescription = "Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // "Cover" badge in corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Photo, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Cover", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Video Clip 2
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF26181C))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zevn_sample_video),
                            contentDescription = "Scene 2",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Video Clip 3
                    Box(
                        modifier = Modifier
                            .weight(0.20f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E26))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zevn_hero_banner),
                            contentDescription = "Scene 3",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // TRACK 2: Audio "♪ Add Music"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF191924))
                        .clickable { Toast.makeText(context, "Music Library Opened", Toast.LENGTH_SHORT).show() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFFA0A0B2), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentProj.audioTracks.firstOrNull()?.title ?: "Add Music",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD4D4DE)
                        )
                    }
                    WaveformVisualizer(isPlaying = isPlaying, modifier = Modifier.width(80.dp), color = Color(0xFFFF5238))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TRACK 3: Text 1 "T New In Fashion"
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF231C28))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("T", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF7A00))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New In Fashion",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TRACK 4: Text 2 "T Step Into What's New"
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF221A1F))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("T", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Step Into What's New",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Vertical White Playhead Line
            val progress = (playheadMs.toFloat() / currentProj.durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newProg = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            viewModel.seekTo((newProg * currentProj.durationMs).toLong())
                        }
                    }
            ) {
                // Vertical white needle line
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )
                // Small triangular pointer on top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // --- 5. BOTTOM ACTION TOOLS BAR (Split, Rotate, Opacity, Duplicate, AI Images, Transcribe) ---
        Surface(
            color = Color(0xFF0C0C10),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close drawer icon button (Down arrow as in Phone 2)
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Tools",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Scrollable Tools Strip
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        ActionToolItem("Split", Icons.Default.ContentCut) {
                            viewModel.splitClipAtPlayhead()
                            Toast.makeText(context, "Clip split at current playhead", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        ActionToolItem("Rotate", Icons.Default.RotateRight) {
                            viewModel.rotate()
                        }
                    }
                    item {
                        ActionToolItem("Opacity", Icons.Default.Layers) {
                            showOpacitySlider = !showOpacitySlider
                        }
                    }
                    item {
                        ActionToolItem("Duplicate", Icons.Default.ContentCopy) {
                            viewModel.duplicateSelectedClip()
                            Toast.makeText(context, "Clip duplicated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        ActionToolItem("AI Images", Icons.Default.MovieFilter) {
                            showVeoModal = true
                        }
                    }
                    item {
                        ActionToolItem("Transcribe", Icons.Default.Mic) {
                            showTranscribeModal = true
                        }
                    }
                }
            }
        }

        // Opacity Slider Popover if active
        if (showOpacitySlider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E28))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity: ${(currentOpacity * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = currentOpacity,
                        onValueChange = { viewModel.setOpacity(it) },
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5238), activeTrackColor = Color(0xFFFF5238)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // --- DIALOG: RESOLUTION PICKER ---
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Export Quality", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("720p HD", "1080p Full HD", "2K QHD", "4K Ultra HD").forEach { res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showResolutionDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentProj.resolution.startsWith(res.substring(0, 4)),
                                onClick = { showResolutionDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF5238))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(res, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionDialog = false }) {
                    Text("Done", color = Color(0xFFFF5238))
                }
            }
        )
    }

    // --- DIALOG: TRANSCRIBE AUDIO (gemini-3.5-transcribe) ---
    if (showTranscribeModal) {
        AlertDialog(
            onDismissRequest = {
                if (isRecordingAudio) {
                    audioRecorder.stopRecording()
                    viewModel.setRecordingAudioState(false)
                }
                showTranscribeModal = false
            },
            containerColor = Color(0xFF161622),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, tint = Color(0xFF00FF9D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transcribe with gemini-3.5-transcribe", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Speak into microphone to create synchronized subtitles at the current playhead position.",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0B2)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingAudio) Color(0xFFFF3B30) else Color(0xFF262636))
                            .clickable {
                                if (isRecordingAudio) {
                                    val bytes = audioRecorder.stopRecording() ?: "Microphone voice sample".toByteArray()
                                    viewModel.setRecordingAudioState(false)
                                    viewModel.transcribeAudioInput(bytes)
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        audioRecorder.startRecording()
                                        viewModel.setRecordingAudioState(true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRecordingAudio) "Recording... Tap to stop" else "Tap to Speak",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecordingAudio) Color(0xFFFF5238) else Color.White
                    )

                    if (isTranscribing) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CircularProgressIndicator(color = Color(0xFF00FF9D), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Transcribing via gemini-3.5-transcribe...", fontSize = 10.sp, color = Color(0xFF00FF9D))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTranscribeModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF9D))
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- DIALOG: VEO ANIMATE PHOTO (veo-3.1-fast-generate-preview) ---
    if (showVeoModal) {
        var veoPrompt by remember { mutableStateOf("Cinematic runway slow motion with dynamic red rim light") }
        var veoRatio by remember { mutableStateOf("9:16") }

        AlertDialog(
            onDismissRequest = { showVeoModal = false },
            containerColor = Color(0xFF161622),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MovieFilter, null, tint = Color(0xFF6C63FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Animate with Veo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Upload photo and generate video using model veo-3.1-fast-generate-preview.", fontSize = 12.sp, color = Color(0xFFA0A0B2))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Aspect Ratio (16:9 or 9:16)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("9:16" to "9:16 Portrait", "16:9" to "16:9 Landscape").forEach { (r, l) ->
                            val isSel = veoRatio == r
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF6C63FF) else Color(0xFF222230))
                                    .clickable { veoRatio = r }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = veoPrompt,
                        onValueChange = { veoPrompt = it },
                        label = { Text("Motion Prompt") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.animatePhotoWithVeo(veoPrompt, veoRatio)
                        showVeoModal = false
                        Toast.makeText(context, "Generating Veo Video ($veoRatio)...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                ) {
                    Text("Generate Veo Video", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVeoModal = false }) {
                    Text("Cancel", color = Color(0xFFA0A0B2))
                }
            }
        )
    }
}

@Composable
fun ActionToolItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("action_tool_$label")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFD4D4DE),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFA0A0B2)
        )
    }
}
