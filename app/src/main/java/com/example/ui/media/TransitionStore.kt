package com.example.ui.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Transitions between video clips. A transition is set on a clip and plays at the START of that clip,
 * blending it with the clip before it. Stored in transitions.json in the project folder.
 * The clip is identified by its source file and its source range.
 */
object TransitionStore {

    /** How long a transition lasts on the timeline. */
    const val LENGTH_MS = 500L

    /** Transition kinds: (key, label). */
    val TYPES: List<Pair<String, String>> = listOf(
        "none" to "None",
        "dissolve" to "Dissolve",
        "fade_black" to "Fade from black",
        "flash" to "Flash white"
    )

    private val _map = MutableStateFlow<Map<String, String>>(emptyMap())
    val map: StateFlow<Map<String, String>> = _map.asStateFlow()

    /** The key that identifies a clip in the transition map. */
    fun keyOf(s: Segment): String = "${s.source}|${s.startMs}|${s.endMs}"

    /** The transition at the start of [s] ("none" if there is none). */
    fun get(s: Segment): String = _map.value[keyOf(s)] ?: "none"

    fun set(s: Segment, type: String) {
        val current = _map.value
        _map.value = if (type == "none") {
            current - keyOf(s)
        } else {
            current + (keyOf(s) to type)
        }
    }

    fun reset() {
        _map.value = emptyMap()
    }

    fun save(context: Context) {
        val o = JSONObject()
        for ((k, v) in _map.value) o.put(k, v)
        File(ProjectStore.currentMediaDir(context), "transitions.json").writeText(o.toString())
    }

    fun load(context: Context) {
        val f = File(ProjectStore.currentMediaDir(context), "transitions.json")
        if (!f.exists()) {
            reset()
            return
        }
        try {
            val o = JSONObject(f.readText())
            val out = mutableMapOf<String, String>()
            o.keys().forEach { key -> out[key] = o.optString(key, "none") }
            _map.value = out
        } catch (e: Exception) {
            reset()
        }
    }
}
