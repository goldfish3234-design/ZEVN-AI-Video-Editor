package com.example.ui.media

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the video the user picked from the phone gallery.
 * Kept outside the ViewModel so the editor preview can read it directly.
 */
object ImportedMedia {
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri.asStateFlow()

    fun setVideo(uri: Uri?) {
        _videoUri.value = uri
    }
}
