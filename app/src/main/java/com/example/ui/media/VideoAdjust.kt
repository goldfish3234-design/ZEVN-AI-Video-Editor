package com.example.ui.media

import android.content.Context
import android.graphics.ColorMatrix
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Colour settings of the project.
 * [brightness], [contrast], [saturation] are in -1..1 (0 = unchanged).
 * [filter] is the preset name (or "custom" after a slider is moved). [effect] is one colour effect (or "none").
 */
data class VideoAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val filter: String = "none",
    val effect: String = "none"
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

/** Colour effects (one at a time). */
object VideoEffects {
    val list: List<Pair<String, String>> = listOf(
        "none" to "None",
        "sepia" to "Sepia",
        "gray" to "Grayscale",
        "invert" to "Invert",
        "warmtint" to "Warm Tint",
        "cooltint" to "Cool Tint"
    )
}

// ---- colour maths shared by the preview and the export (4x4, row-major, acting on (r, g, b, 1)) ----

private fun identity(): FloatArray =
    floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

/** Row-major product a * b. */
private fun mul(a: FloatArray, b: FloatArray): FloatArray {
    val o = FloatArray(16)
    for (r in 0..3) {
        for (c in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[r * 4 + k] * b[k * 4 + c]
            o[r * 4 + c] = s
        }
    }
    return o
}

private fun effectMatrix(key: String): FloatArray {
    val m = identity()
    when (key) {
        "sepia" -> {
            val rows = arrayOf(
                floatArrayOf(0.393f, 0.769f, 0.189f),
                floatArrayOf(0.349f, 0.686f, 0.168f),
                floatArrayOf(0.272f, 0.534f, 0.131f)
            )
            for (r in 0..2) for (c in 0..2) m[r * 4 + c] = rows[r][c]
        }
        "gray" -> {
            for (r in 0..2) {
                m[r * 4 + 0] = 0.2126f
                m[r * 4 + 1] = 0.7152f
                m[r * 4 + 2] = 0.0722f
            }
        }
        "invert" -> {
            m[0] = -1f; m[5] = -1f; m[10] = -1f
            m[3] = 1f; m[7] = 1f; m[11] = 1f
        }
        "warmtint" -> { m[0] = 1.1f; m[5] = 1.0f; m[10] = 0.85f }
        "cooltint" -> { m[0] = 0.9f; m[5] = 1.0f; m[10] = 1.15f }
    }
    return m
}

/** The full colour transform: saturation, then contrast, then brightness, then the effect. */
private fun rowMajorFor(a: VideoAdjust): FloatArray {
    // saturation
    val sat = 1f + a.saturation.coerceIn(-1f, 1f)
    val w = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    val s = identity()
    for (i in 0..2) for (j in 0..2) {
        s[i * 4 + j] = (1f - sat) * w[j] + (if (i == j) sat else 0f)
    }
    // contrast around the middle grey
    val k = 1f + a.contrast.coerceIn(-1f, 1f)
    val t = 0.5f * (1f - k)
    val c = identity()
    c[0] = k; c[5] = k; c[10] = k
    c[3] = t; c[7] = t; c[11] = t
    // brightness
    val b = a.brightness.coerceIn(-1f, 1f)
    val br = identity()
    br[3] = b; br[7] = b; br[11] = b
    // effect
    val e = effectMatrix(a.effect)
    return mul(e, mul(br, mul(c, s)))
}

/** Converts to the column-major layout used by the GPU (Media3). */
private fun columnMajor(rowMajor: FloatArray): FloatArray =
    FloatArray(16) { i -> rowMajor[(i % 4) * 4 + i / 4] }

private fun isIdentity(m: FloatArray): Boolean {
    val id = identity()
    for (i in 0 until 16) if (abs(m[i] - id[i]) > 1e-5f) return false
    return true
}

/** A colour matrix effect for the export. */
@UnstableApi
class ColorMatrixEffect(private val matrix: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix.copyOf()
}

/**
 * The export effects for the colour settings. Nothing is added when every value is neutral.
 * Used by TimelineExporter.
 */
@UnstableApi
fun adjustEffects(a: VideoAdjust): List<Effect> {
    val rm = rowMajorFor(a)
    if (isIdentity(rm)) return emptyList()
    return listOf(ColorMatrixEffect(columnMajor(rm)))
}

/** The same colour transform as an Android ColorMatrix, used to draw the live preview. */
fun previewColorMatrix(a: VideoAdjust): ColorMatrix {
    val m = rowMajorFor(a)
    val arr = FloatArray(20)
    for (r in 0..2) {
        for (c in 0..2) arr[r * 5 + c] = m[r * 4 + c]
        arr[r * 5 + 4] = m[r * 4 + 3] * 255f
    }
    arr[18] = 1f // alpha row stays as it is
    return ColorMatrix(arr)
}

/**
 * The colour settings of the current project. Saved in adjust.json in the project folder.
 */
object VideoAdjustStore {
    private val _adjust = MutableStateFlow(VideoAdjust())
    val adjust: StateFlow<VideoAdjust> = _adjust.asStateFlow()

    fun set(value: VideoAdjust) {
        _adjust.value = VideoAdjust(
            brightness = value.brightness.coerceIn(-1f, 1f),
            contrast = value.contrast.coerceIn(-1f, 1f),
            saturation = value.saturation.coerceIn(-1f, 1f),
            filter = value.filter,
            effect = value.effect
        )
    }

    fun reset() {
        _adjust.value = VideoAdjust()
    }

    fun save(context: Context) {
        val v = _adjust.value
        val o = JSONObject()
            .put("brightness", v.brightness.toDouble())
            .put("contrast", v.contrast.toDouble())
            .put("saturation", v.saturation.toDouble())
            .put("filter", v.filter)
            .put("effect", v.effect)
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
                    filter = o.optString("filter", "none"),
                    effect = o.optString("effect", "none")
                )
            )
        } catch (e: Exception) {
            reset()
        }
    }
}
