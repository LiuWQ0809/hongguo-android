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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            val activity = compose.activity
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync { activity.updatePictureInPicture(false, 0, 0) }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            val stopDeadline = System.currentTimeMillis() + 10_000
            while (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && System.currentTimeMillis() < stopDeadline)
                Thread.sleep(100)
            assertFalse(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            var player: Player? = null
            instrumentation.runOnMainSync { player = PlaybackSessionService.activePlayer }
            val active = requireNotNull(player)
            AppSource.setPlaybackPostForTest { _, _, _ -> throw IOException("injected failure") }
            AppSource.setPlaybackTabForTest { _, _ -> throw IOException("injected failure") }
            instrumentation.runOnMainSync { active.seekTo(active.duration - 1_000) }
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && (model.state.value.episodePosition != 1 || model.state.value.preparationStage != "retry"))
                Thread.sleep(50)
            assertEquals("The injected next-episode failure must trigger a retry", "retry", model.state.value.preparationStage)
            instrumentation.runOnMainSync { active.pause() }
            AppSource.setPlaybackPostForTest(null)
            AppSource.setPlaybackTabForTest(null)
            var next: Player? = null
            while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
                instrumentation.runOnMainSync { next = PlaybackSessionService.activePlayer }
                Thread.sleep(100)
            }
            val newPlayer = requireNotNull(next)
            assertNotSame("Background retry must create the next player", active, newPlayer)
            instrumentation.runOnMainSync {
                assertFalse("Retry must retain a user's pause request", newPlayer.playWhenReady)
                newPlayer.play()
            }
            Thread.sleep(4_000)
            instrumentation.runOnMainSync { assertTrue("The retried episode must play", newPlayer.currentPosition > 1_000) }
        } finally {
            AppSource.setPlaybackPostForTest(null)
            AppSource.setPlaybackTabForTest(null)
            compose.runOnIdle { model.setPreloadNext(preload); model.setAutoPlayNext(autoplay) }
        }
    }

    @Test fun lockingDuringNextEpisodePreparationStillStartsPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        lateinit var model: HongguoViewModel
        var preload = true
        var autoplay = true
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
            preload = model.state.value.preloadNext; autoplay = model.state.value.autoPlayNext
            model.setPreloadNext(false); model.setAutoPlayNext(true)
            model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
        }
        try {
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            compose.runOnIdle { model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull(model.state.value.playbackError)
            var first: Player? = null
            instrumentation.runOnMainSync { first = PlaybackSessionService.activePlayer }
            val active = requireNotNull(first)
            AppSource.setPlaybackPostForTest { _, _, _ -> entered.countDown(); unblock.await(60, TimeUnit.SECONDS); throw IOException("delayed request") }
            instrumentation.runOnMainSync { active.seekTo(active.duration - 1_000) }
            assertTrue("Next episode must begin preparing", entered.await(30, TimeUnit.SECONDS))
            Thread.sleep(2_500)
            assertTrue(model.state.value.preparing)
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            Thread.sleep(15_000) // Past Android's short post-interaction background-start grace period.
            AppSource.setPlaybackPostForTest(null); unblock.countDown()
            val deadline = System.currentTimeMillis() + 90_000
            var next: Player? = null
            while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
                instrumentation.runOnMainSync { next = PlaybackSessionService.activePlayer }
                if (next == null || next === active) Thread.sleep(200)
            }
            val nextPlayer = requireNotNull(next) { "Next episode must start after locking during preparation" }
            assertNotSame(active, nextPlayer)
            Thread.sleep(4_000)
            instrumentation.runOnMainSync { assertTrue(nextPlayer.currentPosition > 1_000) }
        } finally {
            unblock.countDown(); AppSource.setPlaybackPostForTest(null)
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            instrumentation.runOnMainSync { model.setPreloadNext(preload); model.setAutoPlayNext(autoplay) }
        }
    }
}
