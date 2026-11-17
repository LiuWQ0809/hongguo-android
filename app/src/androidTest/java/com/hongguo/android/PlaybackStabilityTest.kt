package com.hongguo.android

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackStabilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun verifyAdvance(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var initial = 0L
        instrumentation.runOnMainSync { initial = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }
        val deadline = SystemClock.elapsedRealtime() + 20000
        while (SystemClock.elapsedRealtime() < deadline) {
            var current = 0L
            instrumentation.runOnMainSync { current = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }
            if (current > initial + 1200) return
            SystemClock.sleep(200)
        }
        fail("$label 播放进度未前进")
    }
    @Test fun multipleShowsRenderAndAdvance() {
        val model = compose.activity.playbackModel
        val queries = listOf("铁血信使", "谁让这个文盲修仙的第六季", "重生")
        for (query in queries) {
            val card = if (query == "铁血信使") DramaCard("7696083096164125721", query, "", 0, "") else {
                compose.runOnIdle { model.searchRecent(query) }
                compose.waitUntil(120000) { !model.state.value.searching }
                assertNull("搜索 $query 失败", model.state.value.searchError)
                requireNotNull(model.state.value.results.firstOrNull {
                    if (query.contains("文盲")) it.title.contains("文盲修仙") && it.title.contains("第六季") else true
                }) { "搜索 $query 无结果" }
            }
            compose.runOnIdle { model.openDrama(card) }
            compose.waitUntil(120000) { !model.state.value.loadingDetail }
            val episodes = requireNotNull(model.state.value.detail?.episodes) { "${card.title}: ${model.state.value.detailError}" }
            assertTrue("${card.title} 少于两集", episodes.size >= 2)
            val indices = if (query.contains("文盲") && episodes.size > 57) listOf(1, 57) else listOf(0, 1)
            for (index in indices) {
                val label = "${card.title} 第${index + 1}集 ${episodes[index].id}"
                compose.runOnIdle { model.playEpisode(index, restart = true) }
                compose.waitUntil(120000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
                assertNull(label, model.state.value.playbackError)
                assertEquals(label, episodes[index].id, model.state.value.video?.episodeId)
                assertTrue(label, model.state.value.firstFrameRendered)
                verifyAdvance(label)
                println("播放通过：$label")
            }
            compose.runOnIdle { model.closePlayer() }
        }
    }
}
