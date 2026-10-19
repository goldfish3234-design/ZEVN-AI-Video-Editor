package com.example.data.model

import com.example.R

data class VideoProject(
    val id: String,
    val title: String,
    val durationMs: Long = 18000L,
    val aspectRatio: AspectRatioType = AspectRatioType.RATIO_9_16,
    val resolution: String = "1080p Full HD",
    val thumbnailRes: Int = R.drawable.zevn_sample_video,
    val clips: List<VideoClip> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val captions: List<CaptionItem> = emptyList(),
    val activeFilter: FilterPreset = FilterPreset.CYBERPUNK,
    val speed: Float = 1.0f,
    val rotationAngle: Int = 0,
    val isFlippedH: Boolean = false,
    val isFlippedV: Boolean = false,
    val createdAt: String = "Just now",
    val status: String = "Draft"
)

enum class AspectRatioType(val label: String, val ratio: Float, val iconName: String) {
    RATIO_9_16("9:16 TikTok / Reels", 9f / 16f, "TikTok"),
    RATIO_16_9("16:9 YouTube", 16f / 9f, "YouTube"),
    RATIO_1_1("1:1 Instagram", 1f, "Instagram"),
    RATIO_4_5("4:5 Feed", 4f / 5f, "Feed")
}

data class VideoClip(
    val id: String,
    val title: String,
    val startMs: Long,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val transition: TransitionType = TransitionType.NONE,
    val thumbnailRes: Int = R.drawable.zevn_sample_video
)

enum class TransitionType(val title: String, val desc: String) {
    NONE("None", "Cut"),
    DISSOLVE("Dissolve", "Smooth crossfade"),
    GLITCH("Cyber Glitch", "Digital RGB split"),
    WHIP_PAN("Whip Pan", "High speed blur transition"),
    ZOOM_IN("Zoom In", "Dynamic punch zoom"),
    FLASH("Flash White", "High impact frame flash"),
    SLIDE_LEFT("Slide", "Sleek directional wipe")
}

data class AudioTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationMs: Long,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val isAutoBeatSynced: Boolean = true
)

data class CaptionItem(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val style: CaptionStyle = CaptionStyle.MR_BEAST
)

enum class CaptionStyle(val title: String, val desc: String) {
    MR_BEAST("MrBeast Punchy", "Bold yellow with thick black outline & pop animation"),
    HORMOZI("Alex Hormozi", "Clean uppercase high-contrast green highlight"),
    NEON_CYAN("Cyberpunk Neon", "Electric cyan & purple glow text"),
    MINIMAL("Minimal Sans", "Clean subtitle bar with subtle backdrop"),
    KARAOKE("Karaoke Word Glow", "Dynamic word-by-word active tracking")
}

enum class FilterPreset(val title: String, val category: String, val brightness: Float, val contrast: Float, val saturation: Float) {
    ORIGINAL("Original", "Natural", 1.0f, 1.0f, 1.0f),
    CYBERPUNK("Cyberpunk Neon", "Cinematic", 1.05f, 1.35f, 1.45f),
    TEAL_ORANGE("Teal & Orange", "Blockbuster", 1.0f, 1.30f, 1.25f),
    MOODY_NOIR("Moody Noir", "Dramatic", 0.95f, 1.50f, 0.0f),
    RETRO_VHS("Retro VHS 90s", "Vintage", 1.08f, 1.20f, 1.15f),
    GOLDEN_HOUR("Golden Hour", "Vibrant", 1.05f, 1.15f, 1.35f),
    MATRIX_GREEN("Matrix Cyber", "Sci-Fi", 0.98f, 1.40f, 1.10f)
}

data class AiVoice(
    val id: String,
    val name: String,
    val tag: String,
    val tone: String,
    val language: String = "English (US)",
    val isPremium: Boolean = false
)

data class AiAvatar(
    val id: String,
    val name: String,
    val role: String,
    val style: String,
    val imageRes: Int = R.drawable.zevn_avatar_ai
)

data class ExportConfig(
    val resolution: String = "1080p Full HD",
    val format: String = "MP4 (H.264)",
    val fps: Int = 60,
    val removeWatermark: Boolean = true,
    val hdrEnabled: Boolean = true,
    val bitrate: String = "High (24 Mbps)"
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val priceMonthly: String,
    val priceYearly: String,
    val isPopular: Boolean = false,
    val features: List<String>
)

data class UserProfile(
    val id: String = "usr_zevn_9912",
    val name: String = "Alex Vance",
    val email: String = "alex.creator@zevn.ai",
    val subscriptionTier: String = "Pro Plan",
    val aiCredits: Int = 420,
    val maxCredits: Int = 600,
    val usedStorageGb: Float = 14.2f,
    val maxStorageGb: Float = 50.0f,
    val totalVideosCreated: Int = 38,
    val estimatedViews: String = "1.4M",
    val hoursSaved: String = "84 hrs"
)
