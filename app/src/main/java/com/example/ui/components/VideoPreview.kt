package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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

/** Sets the video player's volume to the sound level of the clip that is playing now. */
private fun applyClipVolume(player: ExoPlayer) {
    val segs = TimelineModel.segments.value
    player.volume = segs.getOrNull(player.currentMediaItemIndex)?.volume ?: 1f
}

/**
 * Plays the timeline: video segments one after another (each with its own sound level),
 * and the audio clips at their timeline positions.
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

    val audioPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    // Which audio clip is loaded in the audio player (uri + trim)
    val loadedAudioKey = remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            audioPlayer.release()
        }
    }

    // Puts the audio player at the clip under the playhead (silent when there is none)
    fun syncAudio(playheadMs: Long, playing: Boolean) {
        val idx = TimelineModel.audioIndexAt(playheadMs)
        if (idx < 0) {
            audioPlayer.playWhenReady = false
            return
        }
        val c = TimelineModel.audio.value[idx]
        val key = "${c.uri}|${c.inMs}|${c.outMs}"
        var reloaded = false
        if (key != loadedAudioKey.value) {
            audioPlayer.setMediaItem(
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
            audioPlayer.prepare()
            loadedAudioKey.value = key
            reloaded = true
        }
        audioPlayer.volume = c.volume
        val rel = playheadMs - c.offsetMs
        if (!playing) {
            audioPlayer.playWhenReady = false
            return
        }
        if (reloaded || !audioPlayer.isPlaying || abs(audioPlayer.currentPosition - rel) > 300L) {
            audioPlayer.seekTo(rel)
        }
        audioPlayer.playWhenReady = true
    }

    // Rebuild the playlist when clips change (not when only a volume changes)
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
            player.setMediaItems(items, index, offset)
            player.prepare()
            applyClipVolume(player)
        }
    }

    // A clip's sound level changed
    LaunchedEffect(segments) {
        applyClipVolume(player)
    }

    // Audio clips changed (moved, trimmed, split, added...)
    LaunchedEffect(audioClips) {
        syncAudio(TimelineModel.playheadMs.value, TimelineModel.isPlaying.value)
    }

    // Play / pause both players together
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
        player.seekTo(index, offset)
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
                val ph = before + player.currentPosition
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
