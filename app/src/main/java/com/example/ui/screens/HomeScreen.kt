package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AspectRatioType
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

    var showNewProjectDialog by remember { mutableStateOf(false) }
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

    // Tech picture card: the user's own picture (stored in app storage), or the drawn one
    var techVersion by remember { mutableIntStateOf(0) }
    val techFile = remember(techVersion) { File(context.filesDir, "tech_image.jpg").takeIf { it.exists() } }
    val techPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Cannot read the picture")
                        input.use { src ->
                            File(context.filesDir, "tech_image.jpg").outputStream().use { out -> src.copyTo(out) }
                        }
                    }
                    techVersion++
                } catch (e: Exception) {
                    Toast.makeText(context, "Picture could not be set: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { showNewProjectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp)),
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

        // --- 3. TECH PICTURE CARD (drawn in the app; tap to use your own picture) ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFF1F3A44), RoundedCornerShape(22.dp))
                    .clickable { techPicker.launch(arrayOf("image/*")) }
            ) {
                if (techFile != null) {
                    AsyncImage(
                        model = techFile,
                        contentDescription = "Tech picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    TechPicture(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("Tap to use your own picture", color = Color.White, fontSize = 11.sp)
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
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
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

/**
 * A high quality tech picture drawn in the app: dark gradient, neon grid and circuit traces.
 * It is sharp at any size because it is drawn, not a photo.
 */
@Composable
fun TechPicture(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF05070D), Color(0xFF0B2530), Color(0xFF071018))
            )
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 36.dp.toPx()
            val gridColor = Color(0x2600E5FF)
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += grid
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += grid
            }

            val neon = Color(0xFF00E5FF)
            val violet = Color(0xFF7C4DFF)
            val w = size.width
            val h = size.height

            // circuit traces
            val traces = listOf(
                Offset(w * 0.08f, h * 0.78f) to Offset(w * 0.42f, h * 0.78f),
                Offset(w * 0.42f, h * 0.78f) to Offset(w * 0.42f, h * 0.52f),
                Offset(w * 0.42f, h * 0.52f) to Offset(w * 0.72f, h * 0.52f),
                Offset(w * 0.72f, h * 0.52f) to Offset(w * 0.72f, h * 0.2f),
                Offset(w * 0.55f, h * 0.9f) to Offset(w * 0.92f, h * 0.9f)
            )
            for ((a, b) in traces) {
                drawLine(neon, a, b, strokeWidth = 3f)
            }
            for (node in listOf(
                Offset(w * 0.42f, h * 0.78f),
                Offset(w * 0.42f, h * 0.52f),
                Offset(w * 0.72f, h * 0.52f),
                Offset(w * 0.72f, h * 0.2f)
            )) {
                drawCircle(Color(0xFF05070D), radius = 7f, center = node)
                drawCircle(neon, radius = 7f, center = node, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }

            // glowing accent
            drawCircle(violet.copy(alpha = 0.35f), radius = w * 0.35f, center = Offset(w * 0.85f, h * 0.15f))
            drawCircle(neon.copy(alpha = 0.25f), radius = w * 0.22f, center = Offset(w * 0.12f, h * 0.2f))
        }
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
