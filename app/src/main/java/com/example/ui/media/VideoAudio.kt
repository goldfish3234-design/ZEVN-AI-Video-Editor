package com.example.ui.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Volume of the imported video's own sound (0 = muted, 1 = full). Used by the preview. */
object VideoAudio {
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var lastNonZero = 1f

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (v > 0f) lastNonZero = v
        _volume.value = v
    }

    fun toggleMute() {
        _volume.value = if (_volume.value > 0f) 0f else lastNonZero
    }
}
