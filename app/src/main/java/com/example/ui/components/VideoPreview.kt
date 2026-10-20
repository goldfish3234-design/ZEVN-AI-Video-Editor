package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.media.Segment
import com.example.ui.media.Source
import com.example.ui.media.TimelineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** How many overlapping audio layers can play together in the preview. */
private const val MAX_AUDIO_LANES = 4

/**
 * Sets the video player's volume and speed to the values of the clip that is playing now.
 * Clip volume is applied directly; speed is applied with playback parameters (pitch is kept).
 */
private fun applyClipVolume(player: ExoPlayer) {
    val seg = TimelineModel.segments.value.getOrNull(player.currentMediaItemIndex)
    player.volume = seg?.volume ?: 1f
    val speed = seg?.speed ?: 1f
    if (abs(player.playbackParameters.speed - speed) > 0.001f) {
        player.playbackParameters = PlaybackParameters(speed)
    }
}

/**
 * Converts a position on the timeline inside a clip (timeline ms) to a position in the source
 * file (source ms), using the clip's speed. The player works in source time.
 */
private fun sourcePosition(segmentIndex: Int, timelineOffsetMs: Long): Long {
    val speed = TimelineModel.segments.value.getOrNull(segmentIndex)?.speed ?: 1f
    return (timelineOffsetMs * speed).toLong()
}

/**
 * Plays the timeline: video segments one after another (each with its own sound level and speed),
 * and the audio clips at their timeline positions. Overlapping audio clips play together.
 */
@Composable
fun VideoPreview(
    sources: List<Source>,
    segments: List<Segment>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPlaying by TimelineModel.isPlaying.collectAsState()
    val seekRequest by TimelineModel.seekRequest.collectAsState()
    val audioClips by TimelineModel.audio.collectAsState()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) TimelineModel.setPlaying(false)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    applyClipVolume(this@apply)
                }
            })
        }
    }

    // One audio player per overlapping layer
    val audioPlayers = remember {
        List(MAX_AUDIO_LANES) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }
    // Which audio clip each layer has loaded (uri + trim)
    val loadedKeys = remember { List(MAX_AUDIO_LANES) { mutableStateOf("") } }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            audioPlayers.forEach { it.release() }
        }
    }

    // Puts each audio layer at the clip under the playhead (silent when there is none)
    fun syncAudio(playheadMs: Long, playing: Boolean) {
        val clips = TimelineModel.audio.value
        val lanes = TimelineModel.audioLanes()
        for (lane in 0 until MAX_AUDIO_LANES) {
            val p = audioPlayers[lane]
            val idx = lanes.getOrNull(lane)?.firstOrNull { i ->
                playheadMs >= clips[i].offsetMs && playheadMs < clips[i].offsetMs + clips[i].lengthMs
            } ?: -1
            if (idx < 0) {
                p.playWhenReady = false
                continue
            }
            val c = clips[idx]
            val key = "${c.uri}|${c.inMs}|${c.outMs}"
            var reloaded = false
            if (key != loadedKeys[lane].value) {
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(c.uri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(c.inMs)
                                .setEndPositionMs(c.outMs)
                                .build()
                        )
                        .build()
                )
                p.prepare()
                loadedKeys[lane].value = key
                reloaded = true
            }
            p.volume = c.volume
            if (!playing) {
                p.playWhenReady = false
                continue
            }
            val rel = playheadMs - c.offsetMs
            if (reloaded || !p.isPlaying || abs(p.currentPosition - rel) > 300L) {
                p.seekTo(rel)
            }
            p.playWhenReady = true
        }
    }

    // Rebuild the playlist when clips change (not when only a volume or speed changes)
    val clipKey = segments.map { Triple(it.source, it.startMs, it.endMs) }
    LaunchedEffect(clipKey, sources) {
        if (segments.isNotEmpty() && sources.isNotEmpty()) {
            val items = segments.map { seg ->
                MediaItem.Builder()
                    .setUri(sources[seg.source].uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(seg.startMs)
                            .setEndPositionMs(seg.endMs)
                            .build()
                    )
                    .build()
            }
            val (index, offset) = TimelineModel.locate(TimelineModel.playheadMs.value)
            player.setMediaItems(items, index, sourcePosition(index, offset))
            player.prepare()
            applyClipVolume(player)
        }
    }

    // A clip's sound level or speed changed
    LaunchedEffect(segments) {
        applyClipVolume(player)
    }

    // Audio clips changed (moved, trimmed, split, added, undone...)
    LaunchedEffect(audioClips) {
        syncAudio(TimelineModel.playheadMs.value, TimelineModel.isPlaying.value)
    }

    // Play / pause all players together
    LaunchedEffect(isPlaying) {
        if (isPlaying && player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0, 0L)
        }
        player.playWhenReady = isPlaying
        syncAudio(TimelineModel.playheadMs.value, isPlaying)
    }

    // Jump requested from the timeline (tap or drag)
    LaunchedEffect(seekRequest) {
        val req = seekRequest ?: return@LaunchedEffect
        val (index, offset) = TimelineModel.locate(req.ms)
        player.seekTo(index, sourcePosition(index, offset))
        applyClipVolume(player)
        syncAudio(req.ms, TimelineModel.isPlaying.value)
    }

    // Keep the timeline playhead in sync while the video plays
    LaunchedEffect(player) {
        while (isActive) {
            if (player.isPlaying) {
                val segs = TimelineModel.segments.value
                val idx = player.currentMediaItemIndex
                var before = 0L
                for (k in 0 until minOf(idx, segs.size)) before += segs[k].durationMs
                // the player reports source time; the timeline counts time at the clip's speed
                val speed = segs.getOrNull(idx)?.speed ?: 1f
                val ph = before + (player.currentPosition / speed).toLong()
                TimelineModel.setPlayhead(ph)
                syncAudio(ph, true)
            }
            delay(100L)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}
