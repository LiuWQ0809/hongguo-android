package com.hongguo.android

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Hosts the system media session while a video is playing. */
class PlaybackSessionService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
}
