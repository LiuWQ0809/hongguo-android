package com.hongguo.android

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        startVideo()
        val activity = compose.activity
        var player: Player? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = findPlayer(activity.window.decorView) }
        val active = requireNotNull(player)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            compose.waitUntil(10_000) { activity.isInPictureInPictureMode }
            val pipStart = position(active)
            Thread.sleep(3_000)
            assertTrue("PiP playback must advance", position(active) > pipStart + 1_000)
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            Thread.sleep(1_000)
            val lockedStart = position(active)
            Thread.sleep(3_000)
            assertTrue("Screen-off playback must advance", position(active) > lockedStart + 1_000)
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }
}
