package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Mac v1.2.20 显示《持械入宋：第一季》共 100 集。 */
@RunWith(AndroidJUnit4::class)
class MacCatalogRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun macSeriesHasAllEpisodesAndNativeFrames() {
        lateinit var model: HongguoViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
            model.openDrama(DramaCard("7671966197985856536", "持械入宋：第一季", "", 100, ""))
        }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        val episodes = model.state.value.detail?.episodes
        assertNotNull("剧集详情获取失败: ${model.state.value.detailError}", episodes)
        assertEquals("与 Mac 样本的集数一致", 100, episodes!!.size)
        assertEquals("7671977925326949400", episodes.first().id)
        assertEquals((1..100).toList(), episodes.map { it.index })
        assertEquals(100, episodes.map { it.id }.distinct().size)
        for (index in listOf(0, 99)) {
            compose.runOnIdle { model.playEpisode(index, restart = true) }
            compose.waitUntil(120_000) { !model.state.value.preparing }
            assertNull("第 ${index + 1} 集取流失败: ${model.state.value.playbackError}", model.state.value.playbackError)
            assertEquals(episodes[index].id, model.state.value.video?.episodeId)
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertTrue("第 ${index + 1} 集应渲染原生画面: ${model.state.value.playbackError}", model.state.value.firstFrameRendered)
        }
    }
}
