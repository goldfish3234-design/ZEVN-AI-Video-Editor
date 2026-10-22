package com.example.ui.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.sqrt

/** A quiet part of a source file, in source milliseconds. */
data class SilenceRange(val startMs: Long, val endMs: Long)

/**
 * AutoCut: finds quiet parts of the sound of each video clip and removes them.
 * Runs on the device; no internet or API key is needed.
 * Call the functions from a background thread.
 */
object AutoCut {
    // Loudness below this (on the 16-bit scale, about -40 dB) counts as silence
    private const val SILENCE_LEVEL = 300.0
    // A quiet part must be at least this long to be cut
    private const val MIN_SILENCE_MS = 450L
    // Keep this much sound before and after the speech, so cuts do not sound abrupt
    private const val KEEP_PAD_MS = 120L
    // Pieces shorter than this are dropped
    private const val MIN_PIECE_MS = 200L

    /** Quiet parts of the sound of [uri] between [startMs] and [endMs] (source ms). */
    fun findSilence(context: Context, uri: Uri, startMs: Long, endMs: Long): List<SilenceRange> {
        if (endMs <= startMs) return emptyList()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return emptyList()
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // loudness of each decoded chunk, with its time in microseconds
            val times = ArrayList<Long>()
            val levels = ArrayList<Double>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)
                        val size = extractor.readSampleData(buf!!, 0)
                        val t = extractor.sampleTime
                        if (size < 0 || t > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, t, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIndex >= 0) {
                    if (info.size > 0 && info.presentationTimeUs >= startUs && info.presentationTimeUs <= endUs) {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                            var sum = 0.0
                            var n = 0
                            while (shorts.hasRemaining()) {
                                val s = shorts.get().toDouble()
                                sum += s * s
                                n++
                            }
                            if (n > 0) {
                                times.add(info.presentationTimeUs)
                                levels.add(sqrt(sum / n))
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }
            return buildRanges(times, levels, startMs, endMs)
        } catch (e: Exception) {
            // no audio or unreadable file: nothing to cut
            return emptyList()
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    private fun buildRanges(
        times: List<Long>,
        levels: List<Double>,
        startMs: Long,
        endMs: Long
    ): List<SilenceRange> {
        val out = mutableListOf<SilenceRange>()
        var silentFrom: Long? = null
        for (i in times.indices) {
            val tMs = times[i] / 1000L
            val silent = levels[i] < SILENCE_LEVEL
            if (silent && silentFrom == null) silentFrom = tMs
            if (!silent && silentFrom != null) {
                addRange(out, silentFrom, tMs, startMs, endMs)
                silentFrom = null
            }
        }
        silentFrom?.let { addRange(out, it, endMs, startMs, endMs) }
        return out
    }

    private fun addRange(out: MutableList<SilenceRange>, from: Long, to: Long, startMs: Long, endMs: Long) {
        val a = from.coerceIn(startMs, endMs)
        val b = to.coerceIn(startMs, endMs)
        if (b - a >= MIN_SILENCE_MS) out.add(SilenceRange(a, b))
    }

    /**
     * Builds the new clip list: each clip keeps its sound parts and loses its quiet parts.
     * [silences] maps the index of a segment to its quiet parts (source ms).
     * Returns the new list. If nothing would be cut, the original list is returned.
     */
    fun cutSegments(segments: List<Segment>, silences: Map<Int, List<SilenceRange>>): List<Segment> {
        val out = mutableListOf<Segment>()
        for ((i, seg) in segments.withIndex()) {
            val quiet = silences[i].orEmpty().sortedBy { it.startMs }
            var cursor = seg.startMs
            for (q in quiet) {
                val cutFrom = q.startMs + KEEP_PAD_MS
                val cutTo = q.endMs - KEEP_PAD_MS
                if (cutTo <= cutFrom) continue
                if (cutFrom - cursor >= MIN_PIECE_MS) {
                    out.add(seg.copy(startMs = cursor, endMs = cutFrom))
                }
                cursor = maxOf(cursor, cutTo)
            }
            if (seg.endMs - cursor >= MIN_PIECE_MS) {
                out.add(seg.copy(startMs = cursor, endMs = seg.endMs))
            }
        }
        if (out.isEmpty()) return segments
        return out
    }
}
