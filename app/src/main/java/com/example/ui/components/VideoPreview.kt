package com.example.ui.components

import android.net.Uri
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

/**
 * Plays the timeline: video segments (from one or more sources) one after another,
 * with the background music (if any) playing alongside.
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
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            musicPlayer.release()
        }
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

    // Music: load when the track changes
    LaunchedEffect(music?.uri) {
        val m = music
        if (m == null) {
            musicPlayer.clearMediaItems()
        } else {
            musicPlayer.setMediaItem(MediaItem.fromUri(m.uri))
            musicPlayer.prepare()
            if (m.durationMs > 0L) {
                musicPlayer.seekTo(TimelineModel.playheadMs.value % m.durationMs)
            }
            musicPlayer.playWhenReady = TimelineModel.isPlaying.value
        }
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

        val m = TimelineModel.music.value
        if (m != null && m.durationMs > 0L) {
            if (isPlaying) {
                musicPlayer.seekTo(TimelineModel.playheadMs.value % m.durationMs)
            }
            musicPlayer.playWhenReady = isPlaying
        }
    }

    // Jump requested from the timeline (tap or drag)
    LaunchedEffect(seekRequest) {
        val req = seekRequest ?: return@LaunchedEffect
        val (index, offset) = TimelineModel.locate(req.ms)
        player.seekTo(index, offset)
        val m = TimelineModel.music.value
        if (m != null && m.durationMs > 0L) {
            musicPlayer.seekTo(req.ms % m.durationMs)
        }
    }

    // Keep the timeline playhead in sync while the video plays
    LaunchedEffect(player) {
        while (isActive) {
            if (player.isPlaying) {
                val segs = TimelineModel.segments.value
                val idx = player.currentMediaItemIndex
                var before = 0L
                for (k in 0 until minOf(idx, segs.size)) before += segs[k].durationMs
                TimelineModel.setPlayhead(before + player.currentPosition)
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
