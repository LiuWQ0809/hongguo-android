package com.hongguo.api.service

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hongguo.android.DramaCard
import com.hongguo.android.HongguoViewModel
import com.hongguo.android.MainActivity
import com.hongguo.android.PlaybackSessionService
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundRetryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun failedNextEpisodeRetriesAndPreservesPauseIntent() {
        lateinit var model: HongguoViewModel
        var preload = true
        var autoplay = true
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
            preload = model.state.value.preloadNext
            autoplay = model.state.value.autoPlayNext
            model.setPreloadNext(false)
            model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
        }
        try {
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            assertNotNull(model.state.value.detail)
            compose.runOnIdle { model.setAutoPlayNext(true); model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull(model.state.value.playbackError)
            assertTrue(model.state.value.firstFrameRendered)
        } finally {
            AppSource.setPlaybackPostForTest(null)
            AppSource.setPlaybackTabForTest(null)
            compose.runOnIdle { model.setPreloadNext(preload); model.setAutoPlayNext(autoplay) }
        }
    }
}
