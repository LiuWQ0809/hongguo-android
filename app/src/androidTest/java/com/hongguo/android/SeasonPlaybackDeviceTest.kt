package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeasonPlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun suggestedSeasonsOpenAndPlayNatively() {
        lateinit var model: HongguoViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java] }
        // Mac 版将第十一季标记为“即将上线”，这里只测试已上线的分季。
        val seasons = listOf("7676706788724460568" to "第二季", "7675687876633316377" to "同名作品", "7683455900727659545" to "第五季")
        for ((id, title) in seasons) {
            compose.runOnIdle { model.openDrama(DramaCard(id, title, "", 0, "")) }
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            assertNotNull("$title 详情失败: ${model.state.value.detailError}", model.state.value.detail)
            assertTrue("$title 没有分集", model.state.value.detail!!.episodes.isNotEmpty())
            compose.runOnIdle { model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { !model.state.value.preparing }
            assertNull("$title 取流失败: ${model.state.value.playbackError}", model.state.value.playbackError)
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertTrue("$title 未渲染画面: ${model.state.value.playbackError}", model.state.value.firstFrameRendered)
        }
    }
}
