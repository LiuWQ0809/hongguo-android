package com.hongguo.android

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Hosts the system media session while a video is playing. */
class PlaybackSessionService : MediaSessionService() {
    companion object {
        private var service: PlaybackSessionService? = null
        private var current: Player? = null
        private var background: BackgroundPlayback? = null
        internal val activePlayer: Player? get() = current
        internal val hasBackground: Boolean get() = background != null
        private val handler = Handler(Looper.getMainLooper())
        private val stop = Runnable { if (current == null && background == null) service?.stopSelf() }

        internal fun holdBackground(context: Context, owner: BackgroundPlayback) {
            background?.stop()
            background = owner
            handler.removeCallbacks(stop)
            if (current == null) {
                service?.showPreparing() ?: ContextCompat.startForegroundService(context, Intent(context, PlaybackSessionService::class.java))
            }
        }

        internal fun takeBackground(expected: BackgroundPlayback? = null): Boolean? {
            val owner = background ?: return null
            if (expected != null && owner !== expected) return null
            val wantedPlaying = owner.playWhenReady
            background = null
            owner.stop()
            if (current == null) handler.postDelayed(stop, 1_000)
            return wantedPlaying
        }

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
        if (current == null && background != null) showPreparing()
    }
    private fun showPreparing() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("playback_preparing", "播放准备", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1002, Notification.Builder(this, "playback_preparing").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("红果独立版").setContentText("正在准备播放").setContentIntent(open).build())
    }
    private fun clearSession() {
        session?.let { removeSession(it); it.release() }
        session = null
    }
    private fun replaceSession(player: Player) {
        clearSession()
        session = MediaSession.Builder(this, player).build().also(::addSession)
        getSystemService(NotificationManager::class.java).cancel(1002)
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        takeBackground()
        (application as HongguoApplication).playbackModel.closePlayer()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        takeBackground()
        clearSession()
        if (service === this) service = null
        super.onDestroy()
    }
}
