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
        val queries = listOf("减肥系男生", "铁血信使", "谁让这个文盲修仙的第六季", "重生")
        for (query in queries) {
            val card = if (query == "铁血信使") DramaCard("7696083096164125721", query, "", 0, "") else {
                compose.runOnIdle { model.searchRecent(query) }
                compose.waitUntil(120000) { !model.state.value.searching }
                assertNull("搜索 $query 失败", model.state.value.searchError)
                requireNotNull(model.state.value.results.firstOrNull {
                    if (query.contains("文盲")) it.title.contains("文盲修仙") && it.title.contains("第六季")
                    else if (query == "减肥系男生") it.title.contains("减肥") && it.title.contains("一个亿") else true
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
    @Test fun randomCatalogShowsRenderAndAdvance() {
        val model = compose.activity.playbackModel
        compose.runOnIdle { model.selectTab(AppTab.HOME) }
        compose.waitUntil(120000) { model.state.value.home.loaded && !model.state.value.home.loading }
        assertNull("无法加载随机抽样片库", model.state.value.home.error)
        val candidates = model.state.value.home.items.distinctBy { it.id }
        assertTrue("随机片库少于两部作品", candidates.size >= 2)
        val seed = System.currentTimeMillis()
        val random = kotlin.random.Random(seed)
        val chosen = candidates.shuffled(random).take(2)
        println("随机抽样 seed=$seed 候选数=${candidates.size} 作品=${chosen.joinToString { it.title + ":" + it.id }}")
        for (card in chosen) {
            compose.runOnIdle { model.openDrama(card) }
            compose.waitUntil(120000) { !model.state.value.loadingDetail }
            val episodes = requireNotNull(model.state.value.detail?.episodes) { "${card.title}: ${model.state.value.detailError}" }
            assertTrue("${card.title} 没有分集", episodes.isNotEmpty())
            val index = random.nextInt(episodes.size)
            val label = "${card.title} 第${index + 1}集 ${episodes[index].id}"
            println("随机待播：$label")
            compose.runOnIdle { model.playEpisode(index, restart = true) }
            compose.waitUntil(120000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull(label, model.state.value.playbackError)
            assertEquals(label, episodes[index].id, model.state.value.video?.episodeId)
            assertTrue(label, model.state.value.firstFrameRendered)
            verifyAdvance(label)
            println("随机播放通过：$label")
            compose.runOnIdle { model.closePlayer() }
        }
    }
    @Test fun sustainedNativePlaybackKeepsAdvancing() {
        val model = compose.activity.playbackModel
        compose.runOnIdle { model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, "")) }
        compose.waitUntil(120000) { !model.state.value.loadingDetail }
        assertNotNull(model.state.value.detail)
        compose.runOnIdle { model.playEpisode(0, restart = true) }
        compose.waitUntil(120000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
        assertNull(model.state.value.playbackError)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var previous = 0L
        instrumentation.runOnMainSync { previous = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }
        repeat(3) { part ->
            SystemClock.sleep(10000)
            var current = 0L
            var playing = false
            instrumentation.runOnMainSync {
                val player = PlaybackSessionService.activePlayer
                current = player?.currentPosition ?: 0L
                playing = player?.isPlaying == true
            }
            assertNull(model.state.value.playbackError)
            assertTrue("第${part + 1}段播放进度停滞", playing && current >= previous + 7000)
            println("持续播放通过：第${part + 1}段 $previous->$current 毫秒")
            previous = current
        }
        compose.runOnIdle { model.closePlayer() }
    }

}
