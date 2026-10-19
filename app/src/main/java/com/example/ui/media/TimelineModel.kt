package com.example.ui.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One piece of the imported source video, in source time (ms). */
data class Segment(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Asks the player to jump to a timeline position. The id makes repeated seeks to the same spot trigger again. */
data class SeekRequest(val ms: Long, val id: Long)

/**
 * Timeline state for the imported video.
 * The timeline is a list of segments taken from the source video, played one after another.
 * All times in the public API are timeline time (ms) unless stated otherwise.
 */
object TimelineModel {
    private const val MIN_SEGMENT_MS = 300L

    private val _sourceDurationMs = MutableStateFlow(0L)
    val sourceDurationMs: StateFlow<Long> = _sourceDurationMs.asStateFlow()

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _seekRequest = MutableStateFlow<SeekRequest?>(null)
    val seekRequest: StateFlow<SeekRequest?> = _seekRequest.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val undoStack = ArrayList<List<Segment>>()
    private val redoStack = ArrayList<List<Segment>>()
    private var clipboard: List<Segment> = emptyList()
    private var seekCounter = 0L

    val totalDurationMs: Long
        get() = _segments.value.sumOf { it.durationMs }

    /** Called once the source video is loaded and its duration is known. */
    fun initForVideo(durationMs: Long) {
        if (durationMs <= 0L || _sourceDurationMs.value == durationMs) return
        _sourceDurationMs.value = durationMs
        _segments.value = listOf(Segment(0L, durationMs))
        undoStack.clear()
        redoStack.clear()
        clipboard = emptyList()
        _selected.value = -1
        _playheadMs.value = 0L
        _isPlaying.value = false
        updateFlags()
    }

    /** Called when the user imports a different video. */
    fun resetAll() {
        _sourceDurationMs.value = 0L
        _segments.value = emptyList()
        undoStack.clear()
        redoStack.clear()
        clipboard = emptyList()
        _selected.value = -1
        _playheadMs.value = 0L
        _isPlaying.value = false
        updateFlags()
    }

    /** Maps a timeline time to (segment index, position inside that source segment). */
    fun locate(timeMs: Long): Pair<Int, Long> {
        val list = _segments.value
        if (list.isEmpty()) return Pair(0, 0L)
        var acc = 0L
        for ((i, s) in list.withIndex()) {
            if (timeMs < acc + s.durationMs) return Pair(i, timeMs - acc)
            acc += s.durationMs
        }
        return Pair(list.size - 1, list.last().durationMs)
    }

    fun setPlayhead(ms: Long) {
        _playheadMs.value = ms.coerceIn(0L, totalDurationMs)
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing && _segments.value.isNotEmpty()
    }

    fun togglePlaying() {
        setPlaying(!_isPlaying.value)
    }

    fun requestSeek(ms: Long) {
        val t = ms.coerceIn(0L, totalDurationMs)
        _playheadMs.value = t
        seekCounter++
        _seekRequest.value = SeekRequest(t, seekCounter)
    }

    fun selectAtTime(ms: Long) {
        if (_segments.value.isNotEmpty()) _selected.value = locate(ms).first
    }

    private fun updateFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun commit(newList: List<Segment>) {
        if (newList.isEmpty()) return
        undoStack.add(_segments.value)
        redoStack.clear()
        _segments.value = newList
        if (_playheadMs.value > totalDurationMs) _playheadMs.value = totalDurationMs
        if (_selected.value !in newList.indices) _selected.value = -1
        updateFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(_segments.value)
        _segments.value = undoStack.removeAt(undoStack.size - 1)
        if (_playheadMs.value > totalDurationMs) _playheadMs.value = totalDurationMs
        if (_selected.value !in _segments.value.indices) _selected.value = -1
        updateFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(_segments.value)
        _segments.value = redoStack.removeAt(redoStack.size - 1)
        if (_playheadMs.value > totalDurationMs) _playheadMs.value = totalDurationMs
        if (_selected.value !in _segments.value.indices) _selected.value = -1
        updateFlags()
    }

    /** Returns a new list with the segment containing [timeMs] split in two (if long enough). */
    private fun splitList(list: List<Segment>, timeMs: Long): List<Segment> {
        val out = ArrayList<Segment>()
        var acc = 0L
        for (s in list) {
            val rel = timeMs - acc
            val canSplit = rel > 0L &&
                rel < s.durationMs &&
                rel >= MIN_SEGMENT_MS &&
                s.durationMs - rel >= MIN_SEGMENT_MS
            if (canSplit) {
                val cut = s.startMs + rel
                out.add(Segment(s.startMs, cut))
                out.add(Segment(cut, s.endMs))
            } else {
                out.add(s)
            }
            acc += s.durationMs
        }
        return out
    }

    fun splitAtPlayhead() {
        val t = _playheadMs.value
        val list = _segments.value
        val newList = splitList(list, t)
        if (newList.size == list.size) return
        commit(newList)
        selectAtTime(t)
    }

    fun deleteSelected() {
        val i = _selected.value
        val list = _segments.value
        if (i !in list.indices || list.size <= 1) return
        commit(list.filterIndexed { idx, _ -> idx != i })
        _selected.value = -1
    }

    fun copySelected() {
        val i = _selected.value
        val list = _segments.value
        if (i in list.indices) clipboard = listOf(list[i])
    }

    fun cutSelected() {
        if (_selected.value in _segments.value.indices) {
            copySelected()
            deleteSelected()
        }
    }

    /** Pastes the copied segment at the playhead, splitting the segment under the playhead if needed. */
    fun pasteAtPlayhead() {
        if (clipboard.isEmpty()) return
        val t = _playheadMs.value
        val split = splitList(_segments.value, t)
        var acc = 0L
        var insertAt = 0
        for ((idx, s) in split.withIndex()) {
            if (acc + s.durationMs <= t) insertAt = idx + 1
            acc += s.durationMs
        }
        val out = ArrayList<Segment>(split)
        out.addAll(insertAt, clipboard)
        commit(out)
    }

    /** Timeline time where the selected segment starts, or null if nothing is selected. */
    private fun selectedStart(): Long? {
        val i = _selected.value
        val list = _segments.value
        if (i !in list.indices) return null
        var acc = 0L
        for (k in 0 until i) acc += list[k].durationMs
        return acc
    }

    fun trimStartToPlayhead() {
        val start = selectedStart() ?: return
        val list = _segments.value
        val i = _selected.value
        val s = list[i]
        val rel = _playheadMs.value - start
        if (rel <= 0L || s.durationMs - rel < MIN_SEGMENT_MS) return
        val out = ArrayList<Segment>(list)
        out[i] = Segment(s.startMs + rel, s.endMs)
        commit(out)
    }

    fun trimEndToPlayhead() {
        val start = selectedStart() ?: return
        val list = _segments.value
        val i = _selected.value
        val s = list[i]
        val rel = _playheadMs.value - start
        if (rel < MIN_SEGMENT_MS || rel >= s.durationMs) return
        val out = ArrayList<Segment>(list)
        out[i] = Segment(s.startMs, s.startMs + rel)
        commit(out)
    }
}
