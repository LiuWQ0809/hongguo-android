package com.hongguo.android

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.hongguo.api.service.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps automatic episode changes independent of the stopped Compose frame clock. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class BackgroundPlayback(
    private val context: Context,
    private val model: HongguoViewModel,
    initial: PreparedVideo,
    player: ExoPlayer,
    private val releaseOriginal: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var video = initial
    private var original = true
    private var source: VideoSourceHandle? = null
    private var pinned = false
    var current: ExoPlayer? = player
        private set

    init {
        scope.launch {
            model.state.collect { state ->
                val next = state.video
                if (state.playerOpen && next != null && next.requestId != video.requestId) switchTo(next)
            }
        }
    }

    private fun release() {
        val active = current ?: return
        if (original) { original = false; releaseOriginal() }
        else {
            model.savePosition(video.episodeId, active.currentPosition, video.requestId, force = true)
            source?.cancelActiveReads()
            PlaybackSessionService.detach(active)
            active.release()
            if (pinned) Stream.unpin(video.path)
        }
        source = null
        pinned = false
        current = null
    }

    private fun switchTo(next: PreparedVideo) {
        release()
        video = next
        try {
            if (next.session == null && next.path.isNotBlank()) { Stream.pin(next.path); pinned = true }
            val active = ExoPlayer.Builder(context).build()
            current = active
            active.setAudioAttributes(AudioAttributes.DEFAULT, true)
            active.setHandleAudioBecomingNoisy(true)
            active.setWakeMode(C.WAKE_MODE_NETWORK)
            source = videoSourceHandleFor(next)
            active.setMediaSource(source!!.mediaSource)
            active.seekTo(model.state.value.resumePositionMs)
            active.setPlaybackSpeed(model.state.value.playbackSpeed)
            PlaybackSessionService.attach(context, active)
            active.prepare()
            active.play()
        } catch (_: Exception) {
            release()
            model.onPlaybackError(next.episodeId, next.requestId, false)
        }
    }

    fun stop(): Boolean {
        scope.cancel()
        if (!original) release()
        return original
    }
}
