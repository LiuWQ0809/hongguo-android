package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Live regression for the series that previously returned an empty playback response. */
@RunWith(AndroidJUnit4::class)
class NativePlaybackRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun originalSeriesRendersFirstAndSecondEpisodes() {
        lateinit var model: HongguoViewModel
        compose.runOnIdle {
            model = compose.activity.playbackModel
            model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
        }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        val episodes = model.state.value.detail?.episodes
        assertNotNull("Episode list failed: ${model.state.value.detailError}", episodes)
        assertTrue("Expected at least two episodes", episodes!!.size >= 2)
        assertEquals("7696085244503411774", episodes[0].id)
        for (index in 0..1) {
            compose.runOnIdle { model.playEpisode(index, restart = true) }
            compose.waitUntil(120_000) { !model.state.value.preparing }
            assertNull("Episode ${index + 1}: ${model.state.value.playbackError}", model.state.value.playbackError)
            assertEquals(episodes[index].id, model.state.value.video?.episodeId)
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull("Episode ${index + 1}: ${model.state.value.playbackError}", model.state.value.playbackError)
            assertTrue("Episode ${index + 1} must render a native frame", model.state.value.firstFrameRendered)
        }
    }
}
