package com.example.ui.media

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A video file the user imported (a private copy in the project folder). */
data class Source(val uri: Uri, val durationMs: Long)

/** One piece of a source video, in source time (ms). [source] indexes the sources list. [volume] is the clip's own sound level. */
data class Segment(val source: Int, val startMs: Long, val endMs: Long, val volume: Float = 1f) {
    val durationMs: Long get() = endMs - startMs
}

/** Asks the player to jump to a timeline position. The id makes repeated seeks to the same spot trigger again. */
data class SeekRequest(val ms: Long, val id: Long)

/**
 * Background music / extracted audio.
 * [offsetMs] is where it starts on the timeline. [inMs]..[outMs] is the part of the audio file that is used.
 */
data class MusicTrack(
    val uri: Uri,
    val fileDurationMs: Long,
    val volume: Float = 1f,
    val offsetMs: Long = 0L,
    val inMs: Long = 0L,
    val outMs: Long = fileDurationMs
) {
    val lengthMs: Long get() = outMs - inMs
}

/** Which track the tool bar acts on. */
enum class ActiveTrack { VIDEO, MUSIC }

/**
 * Timeline state. Times in the public API are timeline time (ms) unless stated otherwise.
 */
object TimelineModel {
    private const val MIN_SEGMENT_MS = 300L

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _activeTrack = MutableStateFlow(ActiveTrack.VIDEO)
    val activeTrack: StateFlow<ActiveTrack> = _activeTrack.asStateFlow()

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

    private val _music = MutableStateFlow<MusicTrack?>(null)
    val music: StateFlow<MusicTrack?> = _music.asStateFlow()

    private val undoStack = ArrayList<List<Segment>>()
    private val redoStack = ArrayList<List<Segment>>()
    private var clipboard: List<Segment> = emptyList()
    private var seekCounter = 0L

    val totalDurationMs: Long
        get() = _segments.value.sumOf { it.durationMs }

    fun setActiveTrack(track: ActiveTrack) {
        _activeTrack.value = track
    }

    /** Adds an imported video to the end of the timeline. */
    fun addSource(uri: Uri, durationMs: Long) {
        if (durationMs <= 0L) return
        val index = _sources.value.size
        _sources.value = _sources.value + Source(uri, durationMs)
        commit(_segments.value + Segment(index, 0L, durationMs))
    }

    /** Replaces the whole timeline (used when opening a project). */
    fun restore(sources: List<Source>, segments: List<Segment>, music: MusicTrack?) {
        _sources.value = sources
        _segments.value = segments
        _music.value = music
        undoStack.clear()
        redoStack.clear()
        clipboard = emptyList()
        _selected.value = -1
        _activeTrack.value = ActiveTrack.VIDEO
        _playheadMs.value = 0L
        _isPlaying.value = false
        updateFlags()
    }

    /** Sets the sound level of one clip. Not added to undo history (slider changes are continuous). */
    fun setSegmentVolume(index: Int, volume: Float) {
        val list = _segments.value
        if (index !in list.indices) return
        val out = ArrayList<Segment>(list)
        out[index] = list[index].copy(volume = volume.coerceIn(0f, 1f))
        _segments.value = out
    }

    fun setMusic(uri: Uri, durationMs: Long) {
        if (durationMs <= 0L) return
        _music.value = MusicTrack(uri, durationMs)
    }

    fun setMusicVolume(volume: Float) {
        val m = _music.value ?: return
        _music.value = m.copy(volume = volume.coerceIn(0f, 1f))
    }

    fun removeMusic() {
        _music.value = null
        if (_activeTrack.value == ActiveTrack.MUSIC) _activeTrack.value = ActiveTrack.VIDEO
    }

    /** Moves the music start by [deltaMs] on the timeline. */
    fun moveMusicBy(deltaMs: Long) {
        val m = _music.value ?: return
        val newOffset = (m.offsetMs + deltaMs).coerceIn(0L, totalDurationMs)
        _music.value = m.copy(offsetMs = newOffset)
    }

    /** Moves the music's start edge by [deltaMs]. The music keeps its position on the timeline. */
    fun trimMusicStartBy(deltaMs: Long) {
        val m = _music.value ?: return
        val lo = maxOf(-m.inMs, -m.offsetMs)
        val hi = m.lengthMs - MIN_SEGMENT_MS
        if (hi < lo) return
        val d = deltaMs.coerceIn(lo, hi)
        if (d == 0L) return
        _music.value = m.copy(offsetMs = m.offsetMs + d, inMs = m.inMs + d)
    }

    /** Moves the music's end edge by [deltaMs]. */
    fun trimMusicEndBy(deltaMs: Long) {
        val m = _music.value ?: return
        val lo = MIN_SEGMENT_MS - m.lengthMs
        val hi = m.fileDurationMs - m.outMs
        if (hi < lo) return
        val d = deltaMs.coerceIn(lo, hi)
        if (d == 0L) return
        _music.value = m.copy(outMs = m.outMs + d)
    }

    /** Cuts the music start at the playhead (only if the playhead is inside the music). */
    fun trimMusicStartToPlayhead() {
        val m = _music.value ?: return
        val rel = _playheadMs.value - m.offsetMs
        if (rel <= 0L || rel >= m.lengthMs - MIN_SEGMENT_MS) return
        _music.value = m.copy(offsetMs = _playheadMs.value, inMs = m.inMs + rel)
    }

    /** Cuts the music end at the playhead (only if the playhead is inside the music). */
    fun trimMusicEndToPlayhead() {
        val m = _music.value ?: return
        val rel = _playheadMs.value - m.offsetMs
        if (rel < MIN_SEGMENT_MS || rel >= m.lengthMs) return
        _music.value = m.copy(outMs = m.inMs + rel)
    }

    /** Maps a timeline time to (segment index, position inside that segment). */
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
        if (_segments.value.isNotEmpty()) {
            _selected.value = locate(ms).first
            _activeTrack.value = ActiveTrack.VIDEO
        }
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

    /** Returns a new list with the segment containing [timeMs] split in two (if both parts are long enough). */
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
                out.add(s.copy(endMs = cut))
                out.add(s.copy(startMs = cut))
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
        out[i] = s.copy(startMs = s.startMs + rel)
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
        out[i] = s.copy(endMs = s.startMs + rel)
        commit(out)
    }
}
