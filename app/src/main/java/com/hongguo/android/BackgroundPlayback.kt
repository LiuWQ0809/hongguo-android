package com.hongguo.android

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps automatic episode changes independent of the stopped Compose frame clock. */
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

    private fun switchTo(next: PreparedVideo) { video = next }

    fun stop(): Boolean {
        scope.cancel()
        return original
    }
}
