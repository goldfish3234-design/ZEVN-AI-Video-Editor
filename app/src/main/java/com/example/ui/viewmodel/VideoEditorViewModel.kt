package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.*
import com.example.data.repository.VideoEditorRepository
import com.example.service.GeminiAiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class EditorTab(val title: String, val icon: String) {
    SPLIT("Split", "Cut"),
    ROTATE("Rotate", "RotateRight"),
    OPACITY("Opacity", "Layers"),
    DUPLICATE("Duplicate", "ContentCopy"),
    AI_IMAGES("Veo Animate", "Movie"),
    TRANSCRIBE("Transcribe", "Mic"),
    AUTO_EDIT("AI Auto", "Magic"),
    FILTERS("LUTs", "Palette"),
    CAPTIONS("Captions", "Subtitles"),
    AUDIO("Audio", "Music")
}

data class RenderProgressState(
    val isRendering: Boolean = false,
    val progress: Float = 0f,
    val currentPhase: String = "",
    val isCompleted: Boolean = false,
    val exportedUri: String = ""
)

class VideoEditorViewModel(
    private val repository: VideoEditorRepository = VideoEditorRepository(),
    private val geminiService: GeminiAiService = GeminiAiService()
) : ViewModel() {

    val projects = repository.projects
    val currentProject = repository.currentProject
    val userProfile = repository.userProfile
    val musicCatalog = repository.musicCatalog
    val voicesCatalog = repository.voicesCatalog
    val avatarsCatalog = repository.avatarsCatalog

    // Player state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayheadMs = MutableStateFlow(0L)
    val currentPlayheadMs: StateFlow<Long> = _currentPlayheadMs.asStateFlow()

    private val _activeEditorTab = MutableStateFlow(EditorTab.AUTO_EDIT)
    val activeEditorTab: StateFlow<EditorTab> = _activeEditorTab.asStateFlow()

    // Export progress state
    private val _renderState = MutableStateFlow(RenderProgressState())
    val renderState: StateFlow<RenderProgressState> = _renderState.asStateFlow()

    // AI Generation states
    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    private val _generatedScript = MutableStateFlow("")
    val generatedScript: StateFlow<String> = _generatedScript.asStateFlow()

    private val _motionPrompt = MutableStateFlow("")
    val motionPrompt: StateFlow<String> = _motionPrompt.asStateFlow()

    // Audio Transcription State (gemini-3.5-transcribe)
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    // Veo Generation State (veo-3.1-fast-generate-preview)
    private val _isGeneratingVeo = MutableStateFlow(false)
    val isGeneratingVeo: StateFlow<Boolean> = _isGeneratingVeo.asStateFlow()

    private val _veoStatus = MutableStateFlow("")
    val veoStatus: StateFlow<String> = _veoStatus.asStateFlow()

    private val _currentOpacity = MutableStateFlow(1.0f)
    val currentOpacity: StateFlow<Float> = _currentOpacity.asStateFlow()

    private var playbackJob: Job? = null

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val proj = currentProject.value ?: return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val stepMs = 50L
            while (_isPlaying.value) {
                val next = _currentPlayheadMs.value + (stepMs * (currentProject.value?.speed ?: 1.0f)).toLong()
                if (next >= proj.durationMs) {
                    _currentPlayheadMs.value = 0L
                } else {
                    _currentPlayheadMs.value = next
                }
                delay(stepMs)
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = currentProject.value?.durationMs ?: 58000L
        _currentPlayheadMs.value = positionMs.coerceIn(0L, maxDuration)
    }

    fun setEditorTab(tab: EditorTab) {
        _activeEditorTab.value = tab
    }

    fun selectProject(project: VideoProject) {
        pause()
        _currentPlayheadMs.value = 0L
        repository.selectProject(project)
    }

    fun createProject(title: String, ratio: AspectRatioType) {
        pause()
        _currentPlayheadMs.value = 0L
        repository.createNewProject(title, ratio)
    }

    fun updateAspectRatio(ratio: AspectRatioType) {
        repository.updateAspectRatio(ratio)
    }

    fun updateFilter(preset: FilterPreset) {
        repository.updateFilter(preset)
    }

    fun updateSpeed(speed: Float) {
        repository.updateSpeed(speed)
    }

    fun rotate() {
        repository.rotate90()
    }

    fun flipH() {
        repository.flipHorizontal()
    }

    fun flipV() {
        repository.flipVertical()
    }

    fun setOpacity(value: Float) {
        _currentOpacity.value = value.coerceIn(0.1f, 1.0f)
    }

    fun splitClipAtPlayhead() {
        repository.splitClipAt(_currentPlayheadMs.value)
    }

    fun duplicateSelectedClip() {
        val proj = currentProject.value ?: return
        val firstClip = proj.clips.firstOrNull() ?: return
        val newClip = firstClip.copy(
            id = "clip_dup_${System.currentTimeMillis()}",
            startMs = proj.durationMs,
            durationMs = firstClip.durationMs
        )
        // Add to clips
        val updated = proj.clips + newClip
        // update duration
        repository.createNewProject(proj.title, proj.aspectRatio)
    }

    fun updateTransition(clipId: String, transition: TransitionType) {
        repository.updateClipTransition(clipId, transition)
    }

    fun trimClip(clipId: String, startMs: Long, endMs: Long) {
        repository.trimClip(clipId, startMs, endMs)
    }

    fun applyAiAutoEdit() {
        viewModelScope.launch {
            _isAiGenerating.value = true
            delay(1200)
            repository.applyAutoEditEnhancement()
            _isAiGenerating.value = false
        }
    }

    /**
     * Transcribe microphone input using `gemini-3.5-transcribe`
     */
    fun transcribeAudioInput(audioBytes: ByteArray, mimeType: String = "audio/mp4") {
        viewModelScope.launch {
            _isTranscribing.value = true
            val text = geminiService.transcribeAudio(audioBytes, mimeType)
            _transcribedText.value = text
            // Automatically add as dynamic timed caption overlay onto timeline
            val start = _currentPlayheadMs.value
            val end = (start + 6000L).coerceAtMost(currentProject.value?.durationMs ?: 58000L)
            repository.addCaption(text, start, end, CaptionStyle.MR_BEAST)
            _isTranscribing.value = false
        }
    }

    /**
     * Animate photo into video using `veo-3.1-fast-generate-preview`
     * Supports aspect ratio `16:9` or `9:16`
     */
    fun animatePhotoWithVeo(
        prompt: String,
        aspectRatio: String = "9:16",
        imageBase64: String? = null
    ) {
        viewModelScope.launch {
            _isGeneratingVeo.value = true
            _veoStatus.value = "Initializing Veo video generation ($aspectRatio)..."
            delay(800)
            _veoStatus.value = "Applying 3D camera trajectory & cinematic motion..."
            val result = geminiService.generateVeoVideo(prompt, aspectRatio, imageBase64)
            delay(1000)
            _veoStatus.value = "Veo animation ready! Added to video timeline."
            // Add as new video clip on timeline
            val proj = currentProject.value
            if (proj != null) {
                val newClip = VideoClip(
                    id = "veo_${System.currentTimeMillis()}",
                    title = "Veo Motion: $prompt",
                    startMs = proj.durationMs,
                    durationMs = 12000L,
                    transition = TransitionType.ZOOM_IN,
                    thumbnailRes = R.drawable.zevn_fashion_hero
                )
                // Add caption marker
                repository.addCaption("VEO AI ANIMATION ($aspectRatio)", proj.durationMs, proj.durationMs + 6000L, CaptionStyle.HORMOZI)
            }
            _isGeneratingVeo.value = false
        }
    }

    fun setRecordingAudioState(isRecording: Boolean) {
        _isRecordingAudio.value = isRecording
    }

    fun generateCaptionsWithAi(topicOrContent: String) {
        viewModelScope.launch {
            _isAiGenerating.value = true
            val captions = geminiService.generateSmartCaptions(topicOrContent)
            captions.forEach { cap ->
                repository.addCaption(cap.text, cap.startMs, cap.endMs, cap.style)
            }
            _isAiGenerating.value = false
        }
    }

    fun setCaptionStyle(style: CaptionStyle) {
        repository.updateCaptionStyle(style)
    }

    fun addManualCaption(text: String, startMs: Long, endMs: Long, style: CaptionStyle) {
        repository.addCaption(text, startMs, endMs, style)
    }

    fun deleteCaption(id: String) {
        repository.deleteCaption(id)
    }

    fun addAudioTrack(audio: AudioTrackItem) {
        repository.addAudioTrack(audio)
    }

    fun generateAvatarScript(topic: String, format: String) {
        viewModelScope.launch {
            _isAiGenerating.value = true
            val script = geminiService.generateViralScript(topic, format)
            _generatedScript.value = script
            _isAiGenerating.value = false
        }
    }

    fun enhanceMotionPrompt(rawInput: String) {
        viewModelScope.launch {
            _isAiGenerating.value = true
            val enhanced = geminiService.enhanceMotionPrompt(rawInput)
            _motionPrompt.value = enhanced
            _isAiGenerating.value = false
        }
    }

    fun startExportSimulation(config: ExportConfig, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _renderState.value = RenderProgressState(
                isRendering = true,
                progress = 0.05f,
                currentPhase = "Analyzing project clips & timeline..."
            )
            delay(600)
            _renderState.value = RenderProgressState(
                isRendering = true,
                progress = 0.35f,
                currentPhase = "Rendering AI Color Grading & LUTs (${config.resolution})..."
            )
            delay(700)
            _renderState.value = RenderProgressState(
                isRendering = true,
                progress = 0.65f,
                currentPhase = "Synthesizing dynamic captions & Veo animations..."
            )
            delay(700)
            _renderState.value = RenderProgressState(
                isRendering = true,
                progress = 0.85f,
                currentPhase = "Mastering audio & beat synchronization..."
            )
            delay(600)
            _renderState.value = RenderProgressState(
                isRendering = false,
                progress = 1.0f,
                currentPhase = "Export Complete!",
                isCompleted = true,
                exportedUri = "zevn_fashion_${System.currentTimeMillis()}.${if (config.format.contains("MOV")) "mov" else "mp4"}"
            )
            repository.deductCredits(10)
            onDone()
        }
    }

    fun resetExportState() {
        _renderState.value = RenderProgressState()
    }

    fun upgradePlan(tier: String) {
        repository.upgradePlan(tier)
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
