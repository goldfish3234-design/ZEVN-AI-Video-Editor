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
import com.example.ui.media.VideoAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Plays the timeline: video segments one after another, with the trimmed music
 * starting at its offset on the timeline.
 * Play state, playhead and seeks all go through [TimelineModel].
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
    val music by TimelineModel.music.collectAsState()
    val videoVolume by VideoAudio.volume.collectAsState()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) TimelineModel.setPlaying(false)
                }
            })
        }
    }

    val musicPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            musicPlayer.release()
        }
    }

    // Keeps the music player in step with the playhead.
    // Music plays only while the playhead is inside the music bar.
    fun syncMusic(playheadMs: Long, playing: Boolean) {
        val m = TimelineModel.music.value ?: return
        val rel = playheadMs - m.offsetMs
        val inside = rel >= 0L && rel < m.lengthMs
        if (!playing || !inside) {
            musicPlayer.playWhenReady = false
            return
        }
        if (!musicPlayer.isPlaying || abs(musicPlayer.currentPosition - rel) > 300L) {
            musicPlayer.seekTo(rel)
        }
        musicPlayer.playWhenReady = true
    }

    // Video's own sound level
    LaunchedEffect(videoVolume) {
        player.volume = videoVolume
    }

    // Rebuild the video playlist whenever the timeline changes (import, split, trim, paste, undo...)
    LaunchedEffect(segments, sources) {
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
        }
    }

    // Music file: load the trimmed part when the file or its trim changes
    LaunchedEffect(music?.uri, music?.inMs, music?.outMs) {
        val m = music
        if (m == null) {
            musicPlayer.clearMediaItems()
        } else {
            musicPlayer.setMediaItem(
                MediaItem.Builder()
                    .setUri(m.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(m.inMs)
                            .setEndPositionMs(m.outMs)
                            .build()
                    )
                    .build()
            )
            musicPlayer.prepare()
            syncMusic(TimelineModel.playheadMs.value, TimelineModel.isPlaying.value)
        }
    }

    // Music moved on the timeline
    LaunchedEffect(music?.offsetMs) {
        syncMusic(TimelineModel.playheadMs.value, TimelineModel.isPlaying.value)
    }

    LaunchedEffect(music?.volume) {
        musicPlayer.volume = music?.volume ?: 1f
    }

    // Play / pause both players together
    LaunchedEffect(isPlaying) {
        if (isPlaying && player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0, 0L)
        }
        player.playWhenReady = isPlaying
        syncMusic(TimelineModel.playheadMs.value, isPlaying)
    }

    // Jump requested from the timeline (tap or drag)
    LaunchedEffect(seekRequest) {
        val req = seekRequest ?: return@LaunchedEffect
        val (index, offset) = TimelineModel.locate(req.ms)
        player.seekTo(index, offset)
        syncMusic(req.ms, TimelineModel.isPlaying.value)
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
                syncMusic(ph, true)
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
