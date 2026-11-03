package com.hongguo.android

import android.content.res.Configuration
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun startVideo(): HongguoViewModel {
        lateinit var model: HongguoViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
            model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
        }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        assertNotNull(model.state.value.detail)
        compose.runOnIdle { model.playEpisode(0, restart = true) }
        compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
        assertNull(model.state.value.playbackError)
        assertTrue(model.state.value.firstFrameRendered)
        return model
    }

    @Test fun fullscreenRotatesAndReturnsWithoutRestartingVideo() {
        val model = startVideo()
        val request = model.state.value.video?.requestId
        compose.onNodeWithTag("player_fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertEquals(request, model.state.value.video?.requestId)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("player_top_bar").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player_seek").assertDoesNotExist()
        compose.onNodeWithTag("player_toggle").performClick()
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.onNodeWithTag("player_pause").performClick()
        compose.onNodeWithTag("player_resume").assertExists()
        Thread.sleep(5_500)
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.onNodeWithTag("player_resume").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("player_top_bar").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player_toggle").performClick()
        compose.onNodeWithTag("player_fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertEquals(request, model.state.value.video?.requestId)
    }

    private fun findPlayer(view: View): Player? {
        if (view is PlayerView) return view.player
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun position(player: Player): Long {
        var value = -1L
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value = player.currentPosition }
        return value
    }

    @Test fun pictureInPictureAndScreenOffContinuePlayback() {
        val model = startVideo()
        val activity = compose.activity
        var player: Player? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = findPlayer(activity.window.decorView) }
        val active = requireNotNull(player)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            compose.waitUntil(10_000) { activity.isInPictureInPictureMode }
            Thread.sleep(1_000)
            var expectedAspect = 0f
            var pipAspect = 0f
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                expectedAspect = (active.videoSize.width.toFloat() / active.videoSize.height).coerceIn(1f / 2.39f, 2.39f)
                val view = activity.window.decorView
                pipAspect = view.width.toFloat() / view.height
            }
            assertEquals("PiP must follow the video's aspect ratio", expectedAspect, pipAspect, .12f)
            val pipStart = position(active)
            Thread.sleep(3_000)
            assertTrue("PiP playback must advance", position(active) > pipStart + 1_000)
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            Thread.sleep(1_000)
            assertFalse("The screen must really be off", activity.getSystemService(PowerManager::class.java).isInteractive)
            assertTrue("The lock screen must be active", activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            val lockedStart = position(active)
            Thread.sleep(3_000)
            assertTrue("Screen-off playback must advance", position(active) > lockedStart + 1_000)
            automation.executeShellCommand("input keyevent KEYCODE_MEDIA_PAUSE").close()
            Thread.sleep(500)
            var playing = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = active.playWhenReady }
            assertFalse("Media controls must pause while screen is off", playing)
            automation.executeShellCommand("input keyevent KEYCODE_MEDIA_PLAY").close()
            Thread.sleep(500)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = active.playWhenReady }
            assertTrue("Media controls must resume while screen is off", playing)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(active.duration > 5_000)
                active.seekTo(active.duration - 1_000)
            }
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && (model.state.value.episodePosition < 1 || model.state.value.video == null))
                Thread.sleep(300)
            assertEquals("Screen-off autoplay must advance to the next episode", 1, model.state.value.episodePosition)
            assertNotNull("The next episode must be prepared while screen is off", model.state.value.video)
            assertNull(model.state.value.playbackError)
            var next: Player? = null
            while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { next = PlaybackSessionService.activePlayer }
                if (next == null || next === active) Thread.sleep(300)
            }
            val nextPlayer = requireNotNull(next)
            assertNotSame("The next episode must have a new player", active, nextPlayer)
            val nextStart = position(nextPlayer)
            Thread.sleep(4_000)
            assertTrue("The next episode must play while screen is off", position(nextPlayer) > nextStart + 1_000)
            val request = model.state.value.video?.requestId
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
            automation.executeShellCommand("am start -n com.hongguo.android/.MainActivity -f 0x20000000").close()
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered }
            assertEquals("Unlocking must keep the same episode", request, model.state.value.video?.requestId)
            compose.runOnIdle { model.closePlayer() }
            compose.waitUntil(10_000) { PlaybackSessionService.activePlayer == null }
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }
}
