package com.example.ui.components

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.media.Segment
import com.example.ui.media.TimelineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Plays the imported video as the timeline segments, one after another.
 * Play state, playhead and seeks all go through [TimelineModel].
 */
@Composable
fun VideoPreview(
    uri: Uri,
    segments: List<Segment>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPlaying by TimelineModel.isPlaying.collectAsState()
    val seekRequest by TimelineModel.seekRequest.collectAsState()

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && TimelineModel.sourceDurationMs.value == 0L) {
                        val d = this@apply.duration
                        if (d != C.TIME_UNSET && d > 0L) TimelineModel.initForVideo(d)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        TimelineModel.setPlaying(false)
                    }
                }
            })
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Rebuild the playlist whenever the segments change (split, trim, paste, undo...)
    LaunchedEffect(segments, player) {
        if (segments.isEmpty()) return@LaunchedEffect
        val items = segments.map { seg ->
            MediaItem.Builder()
                .setUri(uri)
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

    LaunchedEffect(isPlaying, player) {
        if (isPlaying && player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0, 0L)
        }
        player.playWhenReady = isPlaying
    }

    // Jump requested from the timeline (tap or drag)
    LaunchedEffect(seekRequest) {
        val req = seekRequest ?: return@LaunchedEffect
        val (index, offset) = TimelineModel.locate(req.ms)
        player.seekTo(index, offset)
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
