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

    @Test fun byteVc2AffectedEpisodeRendersNativeVideo() {
        val model = compose.activity.playbackModel
        compose.runOnIdle { model.searchRecent("谁让这个文盲修仙的第六季") }
        compose.waitUntil(120_000) { !model.state.value.searching }
        assertNull("搜索失败", model.state.value.searchError)
        val card = model.state.value.results.firstOrNull { it.title.contains("文盲修仙") && it.title.contains("第六季") }
        assertNotNull("搜索结果中未找到第六季", card)
        compose.runOnIdle { model.openDrama(card!!) }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        val episodes = model.state.value.detail?.episodes
        assertNotNull(model.state.value.detailError, episodes)
        assertTrue("第六季至少应有 58 集", episodes!!.size >= 58)
        compose.runOnIdle { model.playEpisode(57, restart = true) }
        compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
        assertNull("第 58 集：${model.state.value.playbackError}", model.state.value.playbackError)
        assertTrue("第 58 集必须实际渲染原生画面", model.state.value.firstFrameRendered)
    }

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
