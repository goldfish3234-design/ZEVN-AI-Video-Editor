package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.model.CaptionStyle
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonButton
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

enum class AiStudioTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    AUTO_EDIT("Auto Edit", Icons.Default.AutoFixHigh),
    CAPTIONS("Captions", Icons.Default.Subtitles),
    VOICE_LAB("Voice & Audio", Icons.Default.RecordVoiceOver),
    IMAGE_TO_VIDEO("Veo Motion", Icons.Default.SlowMotionVideo),
    AVATAR("AI Avatar", Icons.Default.Face)
}

@Composable
fun AiStudioScreen(
    viewModel: VideoEditorViewModel,
    initialTabKey: String = "autoedit",
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val voices by viewModel.voicesCatalog.collectAsState()
    val avatars by viewModel.avatarsCatalog.collectAsState()
    val isAiGenerating by viewModel.isAiGenerating.collectAsState()
    val generatedScript by viewModel.generatedScript.collectAsState()
    val motionPrompt by viewModel.motionPrompt.collectAsState()

    var selectedTab by remember {
        mutableStateOf(
            when (initialTabKey) {
                "captions" -> AiStudioTab.CAPTIONS
                "voice" -> AiStudioTab.VOICE_LAB
                "imagetovideo" -> AiStudioTab.IMAGE_TO_VIDEO
                "avatar" -> AiStudioTab.AVATAR
                else -> AiStudioTab.AUTO_EDIT
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Tab Header Strip
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFF0F0F14),
            contentColor = ElectricBlue,
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = DividerDark) }
        ) {
            AiStudioTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == tab) NeonCyan else TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == tab) TextWhite else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            when (selectedTab) {
                AiStudioTab.AUTO_EDIT -> {
                    item {
                        AutoEditSection(
                            isGenerating = isAiGenerating,
                            onApply = {
                                viewModel.applyAiAutoEdit()
                                Toast.makeText(context, "⚡ AI Auto-Edit applied to timeline!", Toast.LENGTH_SHORT).show()
                                onOpenEditor()
                            }
                        )
                    }
                }
                AiStudioTab.CAPTIONS -> {
                    item {
                        CaptionsStudioSection(
                            isGenerating = isAiGenerating,
                            onGenerate = { topic -> viewModel.generateCaptionsWithAi(topic) },
                            onCopySrt = {
                                val srtText = """
1
00:00:00,500 --> 00:00:04,000
⚡ EDIT VIDEOS IN SECONDS WITH AI

2
00:00:04,500 --> 00:00:09,000
🔥 AUTO-CUT SILENCE & ADD BEAT SYNC

3
00:00:09,500 --> 00:00:14,000
🚀 EXPORT 4K CINEMATIC MASTERPIECES
                                """.trimIndent()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SRT Subtitles", srtText))
                                Toast.makeText(context, "Subtitles copied in .SRT format!", Toast.LENGTH_SHORT).show()
                            },
                            onOpenEditor = onOpenEditor
                        )
                    }
                }
                AiStudioTab.VOICE_LAB -> {
                    item {
                        VoiceLabSection(voices = voices)
                    }
                }
                AiStudioTab.IMAGE_TO_VIDEO -> {
                    item {
                        ImageToVideoSection(
                            motionPrompt = motionPrompt,
                            isGenerating = isAiGenerating,
                            onEnhancePrompt = { raw -> viewModel.enhanceMotionPrompt(raw) }
                        )
                    }
                }
                AiStudioTab.AVATAR -> {
                    item {
                        AvatarStudioSection(
                            avatars = avatars,
                            generatedScript = generatedScript,
                            isGenerating = isAiGenerating,
                            onGenerateScript = { topic, style -> viewModel.generateAvatarScript(topic, style) },
                            onOpenEditor = onOpenEditor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoEditSection(
    isGenerating: Boolean,
    onApply: () -> Unit
) {
    var cutSilence by remember { mutableStateOf(true) }
    var autoZoom by remember { mutableStateOf(true) }
    var beatSync by remember { mutableStateOf(true) }
    var autoCaptions by remember { mutableStateOf(true) }
    var colorGrade by remember { mutableStateOf(true) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZevnElectricGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoFixHigh, null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "1-Click AI Auto Edit Pipeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Automated viral editing for TikTok, Shorts & Reels",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Feature toggles
        AiFeatureToggle("Smart Silence Detection & Cut", "Removes dead pauses and stutters under 0.3s", cutSilence) { cutSilence = it }
        AiFeatureToggle("Dynamic Ken Burns & Punch-In Zoom", "Auto zooms at high energy phrases for 90%+ retention", autoZoom) { autoZoom = it }
        AiFeatureToggle("Auto Beat Sync Transitions", "Snaps video cuts strictly to music tempo peaks", beatSync) { beatSync = it }
        AiFeatureToggle("Viral Animated Captions (MrBeast Style)", "Highlights keywords with animated yellow/black strokes", autoCaptions) { autoCaptions = it }
        AiFeatureToggle("Cinematic Cyberpunk LUT Grading", "Applies contrast, electric cyan rim highlights, and tone curve", colorGrade) { colorGrade = it }

        Spacer(modifier = Modifier.height(20.dp))

        NeonButton(
            text = "Apply AI Auto-Edit (15 Credits)",
            icon = Icons.Default.Bolt,
            onClick = onApply,
            isLoading = isGenerating,
            modifier = Modifier.fillMaxWidth(),
            testTag = "auto_edit_pipeline_button"
        )
    }
}

@Composable
fun AiFeatureToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
            Text(subtitle, fontSize = 11.sp, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ElectricBlue,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

@Composable
fun CaptionsStudioSection(
    isGenerating: Boolean,
    onGenerate: (String) -> Unit,
    onCopySrt: () -> Unit,
    onOpenEditor: () -> Unit
) {
    var promptInput by remember { mutableStateOf("High-retention tech review about AI video editing") }
    var selectedLanguage by remember { mutableStateOf("English (US)") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI Caption & Subtitle Generator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("Generate accurate speech-to-text with viral word-by-word highlights", style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            label = { Text("Video Topic / Script Context") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = ElectricBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text("Select Language Target", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("English (US)", "Spanish", "German", "French", "Japanese", "Hindi")) { lang ->
                val isSelected = selectedLanguage == lang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ElectricBlue else SurfaceElevated)
                        .clickable { selectedLanguage = lang }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(lang, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else TextWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton(
                text = "Generate Captions",
                icon = Icons.Default.AutoAwesome,
                onClick = { onGenerate(promptInput) },
                isLoading = isGenerating,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onCopySrt,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Default.Download, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(".SRT", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VoiceLabSection(voices: List<com.example.data.model.AiVoice>) {
    var textInput by remember { mutableStateOf("Welcome to ZEVN, the next generation AI video editor. Create, edit, and dominate.") }
    var selectedVoice by remember { mutableStateOf(voices.firstOrNull()) }
    var isVoicePlaying by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI Voice & Studio Audio Lab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("Ultra-realistic Text-to-Speech & Studio Noise Cancellation", style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Enter script for AI Voice narration") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = ElectricBlue
            ),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Select AI Voice Model", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Spacer(modifier = Modifier.height(8.dp))

        voices.forEach { voice ->
            val isSelected = selectedVoice?.id == voice.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else SurfaceVariantDark)
                    .border(1.dp, if (isSelected) ElectricBlue else CardBorder, RoundedCornerShape(10.dp))
                    .clickable { selectedVoice = voice }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ElectricBlue else SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, null, tint = if (isSelected) Color.Black else TextWhite, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(voice.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                            if (voice.isPremium) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                                }
                            }
                        }
                        Text(voice.tone, fontSize = 11.sp, color = TextMuted)
                    }
                }

                IconButton(
                    onClick = { isVoicePlaying = !isVoicePlaying },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isVoicePlaying && isSelected) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Preview Voice",
                        tint = NeonCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        NeonButton(
            text = "Generate Speech Audio",
            icon = Icons.Default.GraphicEq,
            onClick = { isVoicePlaying = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ImageToVideoSection(
    motionPrompt: String,
    isGenerating: Boolean,
    onEnhancePrompt: (String) -> Unit
) {
    var rawInput by remember { mutableStateOf("Neon sports car racing into cyberpunk night city") }
    var selectedMotion by remember { mutableStateOf("Dynamic 3D Parallax") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI Image to Video (Veo Motion)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("Generate cinematic 4K camera trajectories from still photos", style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.zevn_sample_video),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Selected Source Image", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Select Motion Trajectory", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Dynamic 3D Parallax", "Camera Push In", "Orbit 360°", "Drone Flyover", "Hyperspeed Zoom")) { m ->
                val isSelected = selectedMotion == m
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ElectricBlue else SurfaceElevated)
                        .clickable { selectedMotion = m }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(m, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else TextWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            label = { Text("Prompt Idea") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = ElectricBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (motionPrompt.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .padding(10.dp)
            ) {
                Text("⚡ Optimized Prompt: $motionPrompt", fontSize = 11.sp, color = NeonCyan)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        NeonButton(
            text = "Generate Cinematic Motion",
            icon = Icons.Default.MovieFilter,
            onClick = { onEnhancePrompt(rawInput) },
            isLoading = isGenerating,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AvatarStudioSection(
    avatars: List<com.example.data.model.AiAvatar>,
    generatedScript: String,
    isGenerating: Boolean,
    onGenerateScript: (String, String) -> Unit,
    onOpenEditor: () -> Unit
) {
    var topic by remember { mutableStateOf("Why AI video editing will replace manual trimming in 2026") }
    var selectedAvatar by remember { mutableStateOf(avatars.firstOrNull()) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI Talking Avatar Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("Photorealistic presenters with real-time lip-sync and script generation", style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Spacer(modifier = Modifier.height(14.dp))

        Text("Select AI Presenter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(avatars) { av ->
                val isSelected = selectedAvatar?.id == av.id
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .border(1.5.dp, if (isSelected) ElectricBlue else CardBorder, RoundedCornerShape(12.dp))
                        .clickable { selectedAvatar = av }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = av.imageRes),
                            contentDescription = av.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(av.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextWhite)
                            Text(av.role, fontSize = 9.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Topic for Gemini Script Generator") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = ElectricBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        NeonButton(
            text = "Generate Script & Lip-Sync",
            icon = Icons.Default.AutoAwesome,
            onClick = { onGenerateScript(topic, "TikTok Hook 30s") },
            isLoading = isGenerating,
            modifier = Modifier.fillMaxWidth()
        )

        if (generatedScript.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1E36))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Generated Viral Script:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(generatedScript, fontSize = 11.sp, color = TextWhite, lineHeight = 16.sp)
                }
            }
        }
    }
}
