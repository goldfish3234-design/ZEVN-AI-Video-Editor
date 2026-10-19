package com.example.data.repository

import com.example.R
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VideoEditorRepository {

    private val _projects = MutableStateFlow<List<VideoProject>>(emptyList())
    val projects: StateFlow<List<VideoProject>> = _projects.asStateFlow()

    private val _currentProject = MutableStateFlow<VideoProject?>(null)
    val currentProject: StateFlow<VideoProject?> = _currentProject.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _musicCatalog = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val musicCatalog: StateFlow<List<AudioTrackItem>> = _musicCatalog.asStateFlow()

    private val _voicesCatalog = MutableStateFlow<List<AiVoice>>(emptyList())
    val voicesCatalog: StateFlow<List<AiVoice>> = _voicesCatalog.asStateFlow()

    private val _avatarsCatalog = MutableStateFlow<List<AiAvatar>>(emptyList())
    val avatarsCatalog: StateFlow<List<AiAvatar>> = _avatarsCatalog.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val defaultClips = listOf(
            VideoClip(
                id = "clip_1",
                title = "Cover",
                startMs = 0L,
                durationMs = 20000L,
                transition = TransitionType.GLITCH,
                thumbnailRes = R.drawable.zevn_fashion_hero
            ),
            VideoClip(
                id = "clip_2",
                title = "Runway Studio",
                startMs = 20000L,
                durationMs = 20000L,
                transition = TransitionType.WHIP_PAN,
                thumbnailRes = R.drawable.zevn_hero_banner
            ),
            VideoClip(
                id = "clip_3",
                title = "Step Into What's New",
                startMs = 40000L,
                durationMs = 18000L,
                transition = TransitionType.DISSOLVE,
                thumbnailRes = R.drawable.zevn_sample_video
            )
        )

        val defaultCaptions = listOf(
            CaptionItem(
                id = "cap_1",
                text = "NEW IN FASHION",
                startMs = 0L,
                endMs = 28000L,
                style = CaptionStyle.MR_BEAST
            ),
            CaptionItem(
                id = "cap_2",
                text = "Step Into What's New",
                startMs = 1500L,
                endMs = 30000L,
                style = CaptionStyle.HORMOZI
            ),
            CaptionItem(
                id = "cap_3",
                text = "⚡ ZEVN AI VIDEO EDITOR",
                startMs = 32000L,
                endMs = 58000L,
                style = CaptionStyle.NEON_CYAN
            )
        )

        val defaultAudio = listOf(
            AudioTrackItem(
                id = "audio_1",
                title = "Add Music",
                artist = "ZEVN Fashion Beats",
                genre = "Runway Phonk",
                durationMs = 58000L,
                volume = 0.85f,
                isAutoBeatSynced = true
            )
        )

        val initialProj = VideoProject(
            id = "proj_01",
            title = "NEW IN FASHION",
            durationMs = 58000L,
            aspectRatio = AspectRatioType.RATIO_9_16,
            resolution = "1080p",
            thumbnailRes = R.drawable.zevn_fashion_hero,
            clips = defaultClips,
            audioTracks = defaultAudio,
            captions = defaultCaptions,
            activeFilter = FilterPreset.RETRO_VHS,
            speed = 1.0f,
            rotationAngle = 0,
            status = "Ready to Export"
        )

        val proj2 = VideoProject(
            id = "proj_02",
            title = "AI Avatar Tech Review (Shorts)",
            durationMs = 24000L,
            aspectRatio = AspectRatioType.RATIO_9_16,
            resolution = "1080p Full HD",
            thumbnailRes = R.drawable.zevn_avatar_ai,
            clips = listOf(
                VideoClip("c2_1", "Avatar Introduction", 0L, 12000L, thumbnailRes = R.drawable.zevn_avatar_ai),
                VideoClip("c2_2", "Studio Demo", 12000L, 12000L, thumbnailRes = R.drawable.zevn_hero_banner)
            ),
            audioTracks = defaultAudio,
            captions = listOf(
                CaptionItem("cap_21", "MEET THE FUTURE OF AI VIDEO CREATION", 500L, 6000L, CaptionStyle.HORMOZI)
            ),
            activeFilter = FilterPreset.TEAL_ORANGE,
            status = "Draft"
        )

        val proj3 = VideoProject(
            id = "proj_03",
            title = "Futuristic Studio Showcase 16:9",
            durationMs = 32000L,
            aspectRatio = AspectRatioType.RATIO_16_9,
            resolution = "2K QHD",
            thumbnailRes = R.drawable.zevn_hero_banner,
            clips = listOf(
                VideoClip("c3_1", "Cinematic Reveal", 0L, 16000L, thumbnailRes = R.drawable.zevn_hero_banner),
                VideoClip("c3_2", "Feature Overview", 16000L, 16000L, thumbnailRes = R.drawable.zevn_sample_video)
            ),
            audioTracks = defaultAudio,
            activeFilter = FilterPreset.MOODY_NOIR,
            status = "Exported"
        )

        _projects.value = listOf(initialProj, proj2, proj3)
        _currentProject.value = initialProj

        _musicCatalog.value = listOf(
            AudioTrackItem("m1", "Phonk Cyber Drive", "Kavinsky Beat", "Phonk", 45000L),
            AudioTrackItem("m2", "Synthwave Horizon", "Neon Velocity", "Synthwave", 60000L),
            AudioTrackItem("m3", "Tokyo Midnight Lo-Fi", "Aura Chill", "Lo-Fi", 72000L),
            AudioTrackItem("m4", "Hyper Cinematic Riser", "Epic Studio", "Cinematic", 30000L),
            AudioTrackItem("m5", "Trap Viral TikTok 140", "Metro Surge", "Trap", 48000L)
        )

        _voicesCatalog.value = listOf(
            AiVoice("v1", "Nova", "Popular", "Dynamic & Energetic, TikTok style", isPremium = false),
            AiVoice("v2", "Marcus", "Cinematic", "Deep Hollywood Trailer voice", isPremium = true),
            AiVoice("v3", "Aria", "Expressive", "Warm, engaging storytelling", isPremium = false),
            AiVoice("v4", "Zephyr", "Smooth", "Clear commercial & corporate narration", isPremium = false),
            AiVoice("v5", "Echo", "Futuristic", "Sci-Fi robotic cyber tone", isPremium = true)
        )

        _avatarsCatalog.value = listOf(
            AiAvatar("a1", "Alex Vance", "Tech Creator", "Photorealistic Studio", R.drawable.zevn_avatar_ai),
            AiAvatar("a2", "Maya Lin", "Fashion & Lifestyle", "Modern Influencer", R.drawable.zevn_avatar_ai),
            AiAvatar("a3", "Cipher 7", "Cyberpunk Host", "Futuristic Holographic", R.drawable.zevn_avatar_ai),
            AiAvatar("a4", "Sophia Ray", "Executive & Business", "Sleek Corporate", R.drawable.zevn_avatar_ai)
        )
    }

    fun selectProject(project: VideoProject) {
        _currentProject.value = project
    }

    fun createNewProject(title: String, aspectRatio: AspectRatioType): VideoProject {
        val newProj = VideoProject(
            id = "proj_${System.currentTimeMillis()}",
            title = title.ifBlank { "Untitled ZEVN Project" },
            durationMs = 15000L,
            aspectRatio = aspectRatio,
            resolution = "1080p Full HD",
            thumbnailRes = R.drawable.zevn_sample_video,
            clips = listOf(
                VideoClip("c_${System.currentTimeMillis()}", "Scene 1", 0L, 15000L, thumbnailRes = R.drawable.zevn_sample_video)
            ),
            captions = listOf(
                CaptionItem("cap_new", "✨ CREATED WITH ZEVN AI", 1000L, 5000L, CaptionStyle.MR_BEAST)
            ),
            createdAt = "Just now",
            status = "Draft"
        )
        _projects.update { listOf(newProj) + it }
        _currentProject.value = newProj
        return newProj
    }

    fun updateAspectRatio(aspectRatio: AspectRatioType) {
        _currentProject.update { it?.copy(aspectRatio = aspectRatio) }
    }

    fun updateFilter(filter: FilterPreset) {
        _currentProject.update { it?.copy(activeFilter = filter) }
    }

    fun updateSpeed(speed: Float) {
        _currentProject.update { it?.copy(speed = speed) }
    }

    fun rotate90() {
        _currentProject.update { proj ->
            proj?.let {
                val nextAngle = (it.rotationAngle + 90) % 360
                it.copy(rotationAngle = nextAngle)
            }
        }
    }

    fun flipHorizontal() {
        _currentProject.update { it?.copy(isFlippedH = !(it.isFlippedH)) }
    }

    fun flipVertical() {
        _currentProject.update { it?.copy(isFlippedV = !(it.isFlippedV)) }
    }

    fun splitClipAt(playheadMs: Long) {
        _currentProject.update { proj ->
            proj ?: return@update null
            val clips = proj.clips.toMutableList()
            // Find which clip contains playheadMs
            var accumulated = 0L
            val clipIndex = clips.indexOfFirst {
                val end = accumulated + it.durationMs
                val found = playheadMs in accumulated until end
                accumulated = end
                found
            }
            if (clipIndex != -1) {
                val original = clips[clipIndex]
                val splitOffset = playheadMs - (accumulated - original.durationMs)
                if (splitOffset > 1000L && (original.durationMs - splitOffset) > 1000L) {
                    val firstHalf = original.copy(
                        id = "${original.id}_part1",
                        durationMs = splitOffset
                    )
                    val secondHalf = original.copy(
                        id = "${original.id}_part2",
                        startMs = original.startMs + splitOffset,
                        durationMs = original.durationMs - splitOffset,
                        transition = TransitionType.GLITCH
                    )
                    clips.removeAt(clipIndex)
                    clips.add(clipIndex, secondHalf)
                    clips.add(clipIndex, firstHalf)
                    return@update proj.copy(clips = clips)
                }
            }
            proj
        }
    }

    fun updateClipTransition(clipId: String, transition: TransitionType) {
        _currentProject.update { proj ->
            proj?.let {
                val updatedClips = it.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transition = transition) else clip
                }
                it.copy(clips = updatedClips)
            }
        }
    }

    fun trimClip(clipId: String, trimStartMs: Long, trimEndMs: Long) {
        _currentProject.update { proj ->
            proj?.let {
                val updatedClips = it.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(trimStartMs = trimStartMs, trimEndMs = trimEndMs) else clip
                }
                val newDuration = updatedClips.sumOf { c -> c.durationMs - c.trimStartMs - c.trimEndMs }.coerceAtLeast(2000L)
                it.copy(clips = updatedClips, durationMs = newDuration)
            }
        }
    }

    fun applyAutoEditEnhancement() {
        _currentProject.update { proj ->
            proj?.let {
                val smartCaptions = listOf(
                    CaptionItem("cap_auto1", "🚀 WATCH THIS TRANSFORMATION!", 500L, 4000L, CaptionStyle.MR_BEAST),
                    CaptionItem("cap_auto2", "⚡ AI AUTO-CUT ALL SILENCE", 4500L, 9000L, CaptionStyle.HORMOZI),
                    CaptionItem("cap_auto3", "🔥 SYNCS WITH THE BEAT SEAMLESSLY", 9500L, 14000L, CaptionStyle.NEON_CYAN)
                )
                it.copy(
                    activeFilter = FilterPreset.CYBERPUNK,
                    captions = smartCaptions,
                    speed = 1.1f,
                    status = "AI Enhanced"
                )
            }
        }
        // Deduct AI credits
        _userProfile.update { it.copy(aiCredits = (it.aiCredits - 15).coerceAtLeast(0)) }
    }

    fun addCaption(text: String, startMs: Long, endMs: Long, style: CaptionStyle) {
        val newCap = CaptionItem(
            id = "cap_${System.currentTimeMillis()}",
            text = text,
            startMs = startMs,
            endMs = endMs,
            style = style
        )
        _currentProject.update { proj ->
            proj?.let { it.copy(captions = it.captions + newCap) }
        }
    }

    fun updateCaptionStyle(style: CaptionStyle) {
        _currentProject.update { proj ->
            proj?.let { p ->
                p.copy(captions = p.captions.map { it.copy(style = style) })
            }
        }
    }

    fun deleteCaption(captionId: String) {
        _currentProject.update { proj ->
            proj?.let { p ->
                p.copy(captions = p.captions.filterNot { it.id == captionId })
            }
        }
    }

    fun addAudioTrack(audio: AudioTrackItem) {
        _currentProject.update { proj ->
            proj?.let { it.copy(audioTracks = it.audioTracks + audio) }
        }
    }

    fun deductCredits(amount: Int) {
        _userProfile.update { it.copy(aiCredits = (it.aiCredits - amount).coerceAtLeast(0)) }
    }

    fun upgradePlan(newTier: String) {
        _userProfile.update { it.copy(subscriptionTier = newTier, maxCredits = if (newTier.contains("Premium")) 2000 else 1000) }
    }
}
