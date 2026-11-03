package com.hongguo.android

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
