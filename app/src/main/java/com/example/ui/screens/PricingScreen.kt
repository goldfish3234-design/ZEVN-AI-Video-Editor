package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubscriptionPlan
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

@Composable
fun PricingScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    var isYearly by remember { mutableStateOf(false) }

    val plans = listOf(
        SubscriptionPlan(
            id = "free",
            name = "Starter Free",
            priceMonthly = "$0",
            priceYearly = "$0",
            features = listOf(
                "Watermarked 720p HD Exports",
                "100 AI Generation Credits",
                "Basic Timeline Video Trimming",
                "5 GB Cloud Storage",
                "Standard Render Queue"
            )
        ),
        SubscriptionPlan(
            id = "pro",
            name = "ZEVN Pro",
            priceMonthly = "$9.99/mo",
            priceYearly = "$7.99/mo (Billed annually)",
            isPopular = true,
            features = listOf(
                "No Watermark • 1080p Full HD",
                "600 AI Generation Credits/mo",
                "Full Auto-Edit & Silence Detection",
                "Animated MrBeast & Hormozi Captions",
                "50 GB Cloud Storage",
                "Commercial License"
            )
        ),
        SubscriptionPlan(
            id = "premium",
            name = "Creator Studio",
            priceMonthly = "$19.99/mo",
            priceYearly = "$15.99/mo (Billed annually)",
            features = listOf(
                "Unrestricted 4K Ultra HD & 60fps",
                "2,000 AI Credits / Priority GPU queue",
                "AI Voice Cloning & Studio Cleaning",
                "AI Talking Avatar Production",
                "Veo Cinematic Motion Engine",
                "Unlimited Cloud Storage & VIP Support"
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ZEVN Creator Plans",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Supercharge Your Content",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unlock 4K export, voice cloning, and unlimited AI editing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Billing Cycle Switcher
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isYearly) ElectricBlue else Color.Transparent)
                                .clickable { isYearly = false }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Monthly",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (!isYearly) Color.Black else TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isYearly) ElectricBlue else Color.Transparent)
                                .clickable { isYearly = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Yearly (Save 20%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isYearly) Color.Black else TextMuted
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            items(plans.size) { index ->
                val plan = plans[index]
                val isCurrent = userProfile.subscriptionTier.contains(plan.name.replace("ZEVN ", "").replace("Starter ", ""))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    backgroundColor = if (plan.isPopular) Color(0xFF14192B) else SurfaceDark,
                    borderColor = if (plan.isPopular) ElectricBlue else CardBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextWhite)
                            Text(if (isYearly) plan.priceYearly else plan.priceMonthly, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeonCyan)
                        }
                        if (plan.isPopular) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ZevnElectricGradient)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("MOST POPULAR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DividerDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    plan.features.forEach { feat ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Icon(Icons.Default.Check, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(feat, fontSize = 12.sp, color = TextWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isCurrent) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Current Active Plan", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        NeonButton(
                            text = if (plan.id == "free") "Downgrade to Free" else "Upgrade to ${plan.name}",
                            onClick = {
                                viewModel.upgradePlan(plan.name)
                                Toast.makeText(context, "Upgraded to ${plan.name}!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "upgrade_button_${plan.id}"
                        )
                    }
                }
            }
        }
    }
}
