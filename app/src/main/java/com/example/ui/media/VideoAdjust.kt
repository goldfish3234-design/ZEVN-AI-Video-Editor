package com.example.ui.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import com.example.ui.media.ProjectStore
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Colour adjustment for the whole video of the project.
 * [brightness] and [contrast] are in -1..1 (0 = unchanged). [saturation] is in -1..1 (0 = unchanged).
 * [filter] is the name of the preset that was chosen (for the chips), "none" if custom.
 */
data class VideoAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val filter: String = "none"
)

/** Ready-made looks. Each one sets the three sliders. */
object VideoFilters {
    val list: List<Pair<String, String>> = listOf(
        "none" to "Original",
        "warm" to "Warm",
        "cool" to "Cool",
        "vintage" to "Vintage",
        "cinematic" to "Cinematic",
        "bw" to "Black & White"
    )

    fun preset(key: String): VideoAdjust = when (key) {
        "warm" -> VideoAdjust(brightness = 0.05f, contrast = 0.1f, saturation = 0.15f, filter = key)
        "cool" -> VideoAdjust(brightness = 0f, contrast = 0.1f, saturation = -0.1f, filter = key)
        "vintage" -> VideoAdjust(brightness = 0.05f, contrast = -0.1f, saturation = -0.3f, filter = key)
        "cinematic" -> VideoAdjust(brightness = 0f, contrast = 0.25f, saturation = -0.1f, filter = key)
        "bw" -> VideoAdjust(brightness = 0f, contrast = 0.1f, saturation = -1f, filter = key)
        else -> VideoAdjust()
    }
}

/**
 * The video colour adjustment of the current project. Saved in adjust.json in the project folder.
 */
object VideoAdjustStore {
    private val _adjust = MutableStateFlow(VideoAdjust())
    val adjust: StateFlow<VideoAdjust> = _adjust.asStateFlow()

    fun set(value: VideoAdjust) {
        _adjust.value = VideoAdjust(
            brightness = value.brightness.coerceIn(-1f, 1f),
            contrast = value.contrast.coerceIn(-1f, 1f),
            saturation = value.saturation.coerceIn(-1f, 1f),
            filter = value.filter
        )
    }

    fun reset() {
        _adjust.value = VideoAdjust()
    }

    fun save(context: Context) {
        val o = JSONObject()
            .put("brightness", _adjust.value.brightness.toDouble())
            .put("contrast", _adjust.value.contrast.toDouble())
            .put("saturation", _adjust.value.saturation.toDouble())
            .put("filter", _adjust.value.filter)
        File(ProjectStore.currentMediaDir(context), "adjust.json").writeText(o.toString())
    }

    fun load(context: Context) {
        val f = File(ProjectStore.currentMediaDir(context), "adjust.json")
        if (!f.exists()) {
            reset()
            return
        }
        try {
            val o = JSONObject(f.readText())
            set(
                VideoAdjust(
                    brightness = o.optDouble("brightness", 0.0).toFloat(),
                    contrast = o.optDouble("contrast", 0.0).toFloat(),
                    saturation = o.optDouble("saturation", 0.0).toFloat(),
                    filter = o.optString("filter", "none")
                )
            )
        } catch (e: Exception) {
            reset()
        }
    }
}

/**
 * The export effects for a colour adjustment. Only the non-zero parts are added.
 * Brightness and contrast use an RGB matrix; saturation uses the HSL adjustment (-100..100).
 */
@UnstableApi
fun adjustEffects(a: VideoAdjust): List<Effect> {
    val list = mutableListOf<Effect>()
    if (a.brightness != 0f) {
        list.add(Brightness(a.brightness.coerceIn(-1f, 1f)))
    }
    if (a.contrast != 0f) {
        list.add(Contrast(a.contrast.coerceIn(-1f, 1f)))
    }
    if (a.saturation != 0f) {
        val hsl = HslAdjustment.Builder()
            .adjustSaturation(a.saturation.coerceIn(-1f, 1f) * 100f)
            .build()
        list.add(hsl)
    }
    return list
}
