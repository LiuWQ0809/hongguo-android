package com.hongguo.android

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Hosts the system media session while a video is playing. */
class PlaybackSessionService : MediaSessionService() {
    companion object {
        private var service: PlaybackSessionService? = null
        private var current: Player? = null
        private val handler = Handler(Looper.getMainLooper())
        private val stop = Runnable { if (current == null) service?.stopSelf() }

        fun attach(context: Context, player: Player) {
            handler.removeCallbacks(stop)
            current = player
            service?.replaceSession(player) ?: context.startService(Intent(context, PlaybackSessionService::class.java))
        }

        fun detach(player: Player) {
            if (current !== player) return
            current = null
            service?.clearSession()
            handler.postDelayed(stop, 1_000)
        }
    }

    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        service = this
        current?.let(::replaceSession)
    }
    private fun clearSession() {
        session?.let { removeSession(it); it.release() }
        session = null
    }
    private fun replaceSession(player: Player) {
        clearSession()
        session = MediaSession.Builder(this, player).build().also(::addSession)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        clearSession()
        if (service === this) service = null
        super.onDestroy()
    }
}
