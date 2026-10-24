package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AspectRatioType
import com.example.ui.media.ProjectStore
import com.example.ui.media.ProjectSummary
import com.example.ui.media.TimelineModel
import com.example.ui.viewmodel.VideoEditorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Muted = Color(0xFF8A8AA0)
private val Faint = Color(0xFF5C5C72)
private val Surface = Color(0xFF15151F)
private val Outline = Color(0xFF24243A)
private val Accent = Brush.horizontalGradient(listOf(Color(0xFFFF5238), Color(0xFFFF8A3D)))

@Composable
fun HomeScreen(
    viewModel: VideoEditorViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToPricing: () -> Unit
) {
    val context = LocalContext.current

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var draftsVersion by remember { mutableIntStateOf(0) }
    val drafts = remember(draftsVersion) { ProjectStore.list(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10)),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        // --- Greeting ---
        item {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                Text(
                    text = "Good day",
                    fontSize = 14.sp,
                    color = Muted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create something\nbeautiful.",
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
            }
        }

        // --- New project ---
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Accent)
                    .clickable { showNewProjectDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New project",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New project",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Recent projects ---
        item {
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = "RECENT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (drafts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Outline, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your projects will appear here",
                        color = Faint,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(drafts, key = { it.id }) { draft ->
                DraftRow(
                    draft = draft,
                    onOpen = {
                        val state = ProjectStore.open(context, draft.id)
                        if (state != null) {
                            TimelineModel.restore(state.sources, state.segments, state.music)
                            onNavigateToEditor()
                        } else {
                            Toast.makeText(context, "This project could not be opened", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = {
                        ProjectStore.delete(context, draft.id)
                        draftsVersion++
                    }
                )
            }
        }
    }

    // --- New project dialog. Saved projects are never touched: the old one stays in Recent. ---
    if (showNewProjectDialog) {
        var projTitle by remember { mutableStateOf("New Project") }
        var projRatio by remember { mutableStateOf(AspectRatioType.RATIO_9_16) }

        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = Color(0xFF161622),
            title = { Text("New project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = projTitle,
                        onValueChange = { projTitle = it },
                        label = { Text("Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF5238)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Format", color = Muted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    AspectRatioType.values().toList().chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { r ->
                                val isSelected = projRatio == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFFFF5238) else Color(0xFF222230))
                                        .clickable { projRatio = r }
                                        .padding(vertical = 9.dp, horizontal = 4.dp),
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
                    Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel", color = Muted)
                }
            }
        )
    }
}

@Composable
private fun DraftRow(
    draft: ProjectSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .clickable { onOpen() }
            .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = dateFormat.format(Date(draft.updatedAt)),
                color = Muted,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete project",
                tint = Faint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
