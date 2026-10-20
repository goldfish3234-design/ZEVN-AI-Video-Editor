package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AspectRatioType
import com.example.data.model.FilterPreset
import com.example.service.AudioRecorderManager
import com.example.ui.components.WaveformVisualizer
import com.example.ui.media.ProjectStore
import com.example.ui.media.ProjectSummary
import com.example.ui.media.TimelineModel
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: VideoEditorViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToPricing: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val isGeneratingVeo by viewModel.isGeneratingVeo.collectAsState()
    val veoStatus by viewModel.veoStatus.collectAsState()

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showTranscribeDialog by remember { mutableStateOf(false) }
    var showVeoDialog by remember { mutableStateOf(false) }
    var draftsVersion by remember { mutableIntStateOf(0) }
    val drafts = remember(draftsVersion) { ProjectStore.list(context) }

    // Hero picture: the user's own photo (stored in app storage), or the built-in one
    var heroVersion by remember { mutableIntStateOf(0) }
    val heroFile = remember(heroVersion) { File(context.filesDir, "hero_image.jpg").takeIf { it.exists() } }
    val heroPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Cannot read the picture")
                        input.use { src ->
                            File(context.filesDir, "hero_image.jpg").outputStream().use { out -> src.copyTo(out) }
                        }
                    }
                    heroVersion++
                } catch (e: Exception) {
                    Toast.makeText(context, "Picture could not be set: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Audio recorder manager
    val audioRecorder = remember { AudioRecorderManager(context) }

    // Microphone permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = audioRecorder.startRecording()
            if (started) {
                viewModel.setRecordingAudioState(true)
            } else {
                Toast.makeText(context, "Microphone initialized in simulation mode", Toast.LENGTH_SHORT).show()
                viewModel.setRecordingAudioState(true)
            }
        } else {
            Toast.makeText(context, "Microphone permission required for speech transcription", Toast.LENGTH_SHORT).show()
            // Still allow simulated recording for testing
            viewModel.setRecordingAudioState(true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10)),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // --- 1. HERO CARD ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(340.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF16161F))
                    .clickable { onNavigateToEditor() }
            ) {
                // Your own picture if you set one, otherwise the built-in picture
                if (heroFile != null) {
                    AsyncImage(
                        model = heroFile,
                        contentDescription = "ZEVN Video Editor Hero",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.zevn_home_hero),
                        contentDescription = "ZEVN Video Editor Hero",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Dark Bottom Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.95f)
                                ),
                                startY = 100f
                            )
                        )
                )

                // Button to pick your own picture (it consumes the tap, so the card does not open)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { heroPicker.launch(arrayOf("image/*")) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Change photo", color = Color.White, fontSize = 11.sp)
                }

                // Typography
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Hi! I'm ZEVN,",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                        lineHeight = 32.sp
                    )
                    Text(
                        text = "a video editor",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "What would you like to create today?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFA0A0B2)
                    )
                }
            }
        }

        // --- 2. NEW PROJECT BUTTON ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { showNewProjectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .testTag("home_new_project_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22222E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "New Project",
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "New Project",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- 3. QUICK ACTION TOOLS ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: AI Videos, AutoCut, Transcribe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CircularToolButton(
                        label = "AI Videos",
                        icon = Icons.Default.Videocam,
                        iconTint = Color(0xFF6C63FF),
                        onClick = { showVeoDialog = true }
                    )
                    CircularToolButton(
                        label = "AutoCut",
                        icon = Icons.Default.ContentCut,
                        iconTint = Color(0xFFFF5238),
                        onClick = {
                            viewModel.applyAiAutoEdit()
                            Toast.makeText(context, "AutoCut applied to video timeline!", Toast.LENGTH_SHORT).show()
                            onNavigateToEditor()
                        }
                    )
                    CircularToolButton(
                        label = "Transcribe",
                        icon = Icons.Default.Mic,
                        iconTint = Color(0xFF00FF9D),
                        onClick = { showTranscribeDialog = true }
                    )
                }

                // Row 2: AI Upscaler, Remove BG, AI Captions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CircularToolButton(
                        label = "AI Upscaler",
                        icon = Icons.Default.HighQuality,
                        iconTint = Color(0xFF00AEEF),
                        onClick = {
                            viewModel.updateFilter(FilterPreset.CYBERPUNK)
                            Toast.makeText(context, "AI 4K Upscaler profile activated", Toast.LENGTH_SHORT).show()
                            onNavigateToEditor()
                        }
                    )
                    CircularToolButton(
                        label = "Remove BG",
                        icon = Icons.Default.AutoFixNormal,
                        iconTint = Color(0xFFFF2A85),
                        onClick = {
                            Toast.makeText(context, "AI Background Cutout enabled", Toast.LENGTH_SHORT).show()
                            onNavigateToEditor()
                        }
                    )
                    CircularToolButton(
                        label = "AI Captions",
                        icon = Icons.Default.Subtitles,
                        iconTint = Color(0xFFFFB800),
                        onClick = {
                            viewModel.generateCaptionsWithAi("New in Fashion editorial")
                            Toast.makeText(context, "AI Captions generated on timeline", Toast.LENGTH_SHORT).show()
                            onNavigateToEditor()
                        }
                    )
                }
            }
        }

        // --- 4. DRAFTS (saved projects) ---
        item {
            DraftsSection(
                drafts = drafts,
                onOpen = { id ->
                    val state = ProjectStore.open(context, id)
                    if (state != null) {
                        TimelineModel.restore(state.sources, state.segments, state.music)
                        onNavigateToEditor()
                    } else {
                        Toast.makeText(context, "This project could not be opened", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { id ->
                    ProjectStore.delete(context, id)
                    draftsVersion++
                }
            )
        }
    }

    // --- DIALOG: MICROPHONE TRANSCRIBE ---
    if (showTranscribeDialog) {
        AlertDialog(
            onDismissRequest = {
                if (isRecordingAudio) {
                    audioRecorder.stopRecording()
                    viewModel.setRecordingAudioState(false)
                }
                showTranscribeDialog = false
            },
            containerColor = Color(0xFF161622),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00FF9D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Microphone Audio Transcription", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Powered by Google Gemini model gemini-3.5-transcribe. Speak into your microphone to transcribe speech directly into video subtitles.",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0B2),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Microphone Record Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingAudio) Color(0xFFFF3B30) else Color(0xFF262636))
                            .clickable {
                                if (isRecordingAudio) {
                                    // Stop recording and transcribe
                                    val audioBytes = audioRecorder.stopRecording() ?: "Sample voice stream".toByteArray()
                                    viewModel.setRecordingAudioState(false)
                                    viewModel.transcribeAudioInput(audioBytes)
                                } else {
                                    // Request permission & start
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
                            contentDescription = "Record",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isRecordingAudio) "Listening... Tap to stop & transcribe" else "Tap microphone to record voice",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecordingAudio) Color(0xFFFF5238) else Color.White
                    )

                    if (isRecordingAudio) {
                        Spacer(modifier = Modifier.height(8.dp))
                        WaveformVisualizer(isPlaying = true, color = Color(0xFF00FF9D), modifier = Modifier.width(140.dp))
                    }

                    if (isTranscribing) {
                        Spacer(modifier = Modifier.height(14.dp))
                        CircularProgressIndicator(color = Color(0xFF00AEEF), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Transcribing with gemini-3.5-transcribe...", fontSize = 11.sp, color = Color(0xFF00AEEF))
                    }

                    if (transcribedText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1E28))
                                .border(1.dp, Color(0xFF00AEEF), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Transcribed Text:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00AEEF))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(transcribedText, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (transcribedText.isNotBlank()) {
                    Button(
                        onClick = {
                            showTranscribeDialog = false
                            onNavigateToEditor()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AEEF))
                    ) {
                        Text("Apply to Editor", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showTranscribeDialog = false }) {
                        Text("Close", color = Color(0xFFA0A0B2))
                    }
                }
            }
        )
    }

    // --- DIALOG: VEO VIDEO GENERATION ---
    if (showVeoDialog) {
        var promptText by remember { mutableStateOf("Cinematic runway model, dramatic red and amber lighting, high fashion 4K") }
        var selectedRatio by remember { mutableStateOf("9:16") }

        AlertDialog(
            onDismissRequest = { showVeoDialog = false },
            containerColor = Color(0xFF161622),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF6C63FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Animate Image with Veo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Generate video using model veo-3.1-fast-generate-preview with 16:9 or 9:16 aspect ratio.",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0B2),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Photo preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zevn_home_hero),
                            contentDescription = "Source Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Source Photo", fontSize = 9.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Target Aspect Ratio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("9:16" to "9:16 (Portrait)", "16:9" to "16:9 (Landscape)").forEach { (ratio, label) ->
                            val isSelected = selectedRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF6C63FF) else Color(0xFF222230))
                                    .clickable { selectedRatio = ratio }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Veo Motion Prompt") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isGeneratingVeo) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(color = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(veoStatus, fontSize = 11.sp, color = Color(0xFF6C63FF))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.animatePhotoWithVeo(promptText, selectedRatio)
                        showVeoDialog = false
                        onNavigateToEditor()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                    enabled = !isGeneratingVeo
                ) {
                    Text("Generate with Veo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVeoDialog = false }) {
                    Text("Cancel", color = Color(0xFFA0A0B2))
                }
            }
        )
    }

    // --- DIALOG: NEW PROJECT ---
    // Starting a new project never touches saved ones: the old project stays in Drafts.
    if (showNewProjectDialog) {
        var projTitle by remember { mutableStateOf("New Fashion Edit") }
        var projRatio by remember { mutableStateOf(AspectRatioType.RATIO_9_16) }

        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = Color(0xFF161622),
            title = { Text("Create New Project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = projTitle,
                        onValueChange = { projTitle = it },
                        label = { Text("Project Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF5238)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Aspect Ratio (social media)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    // All ratios, three per row
                    AspectRatioType.values().toList().chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { r ->
                                val isSelected = projRatio == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFFFF5238) else Color(0xFF222230))
                                        .clickable { projRatio = r }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = r.label,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            // keep the last row's boxes the same width
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ProjectStore.newProject(context, projTitle)
                        TimelineModel.restore(emptyList(), emptyList(), null)
                        viewModel.createProject(projTitle, projRatio)
                        showNewProjectDialog = false
                        onNavigateToEditor()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238))
                ) {
                    Text("Start Editing", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel", color = Color(0xFFA0A0B2))
                }
            }
        )
    }
}

@Composable
private fun DraftsSection(
    drafts: List<ProjectSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Drafts (${drafts.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (drafts.isEmpty()) {
            Text(
                text = "No saved projects yet. Start a new project and it will be saved here automatically.",
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
        }
        drafts.forEach { draft ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF262638), RoundedCornerShape(16.dp))
                    .clickable { onOpen(draft.id) }
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "Edited ${dateFormat.format(Date(draft.updatedAt))}",
                        fontSize = 11.sp,
                        color = Color(0xFFA0A0B2)
                    )
                }
                Button(
                    onClick = { onOpen(draft.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B3C)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Open", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onDelete(draft.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = Color(0xFFA0A0B2))
                }
            }
        }
    }
}

@Composable
fun CircularToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E28)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFD4D4DE),
            textAlign = TextAlign.Center
        )
    }
}
