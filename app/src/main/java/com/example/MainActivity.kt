package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.ZevnBottomNavigation
import com.example.ui.components.ZevnTopBar
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoEditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ZevnApp()
            }
        }
    }
}

@Composable
fun ZevnApp(
    viewModel: VideoEditorViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf("home") }

    val currentProject by viewModel.currentProject.collectAsState()
    val projects by viewModel.projects.collectAsState()

    // Handle Android system back button
    BackHandler(enabled = currentScreen != "home") {
        currentScreen = "home"
    }

    val showBottomNav = currentScreen in listOf("home", "templates", "drafts")

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10)),
        containerColor = Color(0xFF0C0C10),
        topBar = {
            if (showBottomNav) {
                ZevnTopBar(
                    title = "ZEVN",
                    onOpenPro = { currentScreen = "pricing" },
                    onOpenExport = { currentScreen = "export" },
                    showExportButton = currentProject != null && currentScreen == "editor"
                )
            }
        },
        bottomBar = {
            if (showBottomNav) {
                ZevnBottomNavigation(
                    selectedRoute = currentScreen,
                    onRouteChange = { newRoute ->
                        currentScreen = newRoute
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "home" -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToEditor = { currentScreen = "editor" },
                        onNavigateToPricing = { currentScreen = "pricing" }
                    )
                }
                "templates" -> {
                    TemplatesScreen(
                        onSelectTemplate = {
                            currentScreen = "editor"
                        }
                    )
                }
                "drafts" -> {
                    DraftsScreen(
                        projects = projects,
                        onSelectDraft = { proj ->
                            viewModel.selectProject(proj)
                            currentScreen = "editor"
                        }
                    )
                }
                "editor" -> {
                    EditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = "home" },
                        onOpenExport = { currentScreen = "export" }
                    )
                }
                "pricing" -> {
                    PricingScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = "home" }
                    )
                }
                "export" -> {
                    ExportScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = "editor" }
                    )
                }
            }
        }
    }
}

@Composable
fun TemplatesScreen(
    onSelectTemplate: () -> Unit
) {
    val templates = listOf(
        Triple("Extreme Skyscraper Drone Jump", "Action Vlog • 9:16 Shorts • City", R.drawable.zevn_home_hero),
        Triple("New in Fashion Editorial", "Runway • 9:16 Shorts • Red Neon", R.drawable.zevn_fashion_hero),
        Triple("Cyberpunk Night Drift", "Phonk Beat • 4K 60fps • Glitch", R.drawable.zevn_sample_video),
        Triple("Cinematic Studio Reveal", "Clean Minimal • 16:9 • Whip Pan", R.drawable.zevn_hero_banner),
        Triple("AI Talking Presenter", "Auto Subtitles • Alex Vance • Tech", R.drawable.zevn_avatar_ai)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Text(
                text = "Trending Creator Templates",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "One-click viral presets formatted for TikTok, Reels & Shorts",
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        items(templates) { (title, subtitle, resId) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF262638), RoundedCornerShape(18.dp))
                    .clickable { onSelectTemplate() }
            ) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(subtitle, fontSize = 11.sp, color = Color(0xFFFF7A00))
                }
            }
        }
    }
}

@Composable
fun DraftsScreen(
    projects: List<com.example.data.model.VideoProject>,
    onSelectDraft: (com.example.data.model.VideoProject) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Text(
                text = "Your Saved Drafts (${projects.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Resume editing or duplicate existing timelines",
                fontSize = 12.sp,
                color = Color(0xFFA0A0B2)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        items(projects) { proj ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF262638), RoundedCornerShape(16.dp))
                    .clickable { onSelectDraft(proj) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = proj.thumbnailRes),
                        contentDescription = proj.title,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(proj.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            text = "${proj.resolution} • ${proj.durationMs / 1000}s • ${proj.status}",
                            fontSize = 11.sp,
                            color = Color(0xFFA0A0B2)
                        )
                    }
                }

                Button(
                    onClick = { onSelectDraft(proj) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
