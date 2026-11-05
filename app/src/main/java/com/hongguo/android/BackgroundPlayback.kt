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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private var retry: Job? = null
    private var retryEpisode: String? = null
    private var retryCount = 0
    var current: ExoPlayer? = player
        private set

    init {
        scope.launch {
            model.state.collect { state ->
                val next = state.video
                if (state.playerOpen && next != null && next.requestId != video.requestId) switchTo(next)
                if (state.playerOpen && state.autoPlayNext && next == null && !state.preparing && state.playbackError != null)
                    retryNext(state)
            }
        }
        scope.launch {
            while (true) {
                val active = current
                if (!original && active != null && active.isPlaying)
                    model.savePosition(video.episodeId, active.currentPosition, video.requestId)
                delay(2_000)
            }
        }
    }

    private fun retryNext(state: HongguoUiState) {
        val episode = state.detail?.episodes?.getOrNull(state.episodePosition)?.id ?: return
        if (episode == video.episodeId) return
        if (retryEpisode != episode) { retryEpisode = episode; retryCount = 0; retry?.cancel() }
        if (retry?.isActive == true || retryCount >= 2) return
        retryCount++
        retry = scope.launch {
            delay(if (retryCount == 1) 1_500 else 4_000)
            val latest = model.state.value
            if (latest.playerOpen && latest.autoPlayNext && !latest.preparing && latest.video == null && latest.playbackError != null &&
                latest.detail?.episodes?.getOrNull(latest.episodePosition)?.id == episode) model.retryPlayback()
        }
    }

    private fun release() {
        val active = current ?: return
        if (original) { original = false; releaseOriginal() }
        else {
            val ended = active.playbackState == Player.STATE_ENDED
            model.savePosition(video.episodeId, if (ended) 0 else active.currentPosition, video.requestId,
                force = true, completed = ended)
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
        retry?.cancel(); retry = null; retryEpisode = null; retryCount = 0
        val wantedPlaying = current?.playWhenReady ?: true
        release()
        video = next
        try {
            if (next.session == null && next.path.isNotBlank()) { Stream.pin(next.path); pinned = true }
            val active = ExoPlayer.Builder(context).build()
            current = active
            active.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (current === active && isPlaying) model.onPlaybackStarted(next.episodeId, next.requestId)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (current !== active) return
                    if (state == Player.STATE_ENDED) model.onPlaybackEnded(next.episodeId, next.requestId,
                        active.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0)
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (current === active) model.onPlaybackError(next.episodeId, next.requestId, error.errorCode in 4000..4999)
                }
            })
            active.setAudioAttributes(AudioAttributes.DEFAULT, true)
            active.setHandleAudioBecomingNoisy(true)
            active.setWakeMode(C.WAKE_MODE_NETWORK)
            source = videoSourceHandleFor(next)
            active.setMediaSource(source!!.mediaSource)
            active.seekTo(model.state.value.resumePositionMs)
            active.setPlaybackSpeed(model.state.value.playbackSpeed)
            PlaybackSessionService.attach(context, active)
            active.prepare()
            active.playWhenReady = wantedPlaying
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
