package com.hongguo.api.service

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hongguo.android.DramaCard
import com.hongguo.android.HongguoViewModel
import com.hongguo.android.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LiveTabPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nativeVideoTabFallbackRenders() {
        AppSource.setPlaybackPostForTest { _, _, _ -> throw IOException("test primary failure") }
        try {
            lateinit var model: HongguoViewModel
            compose.runOnIdle {
                model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
                model.openDrama(DramaCard("7671966197985856536", "持械入宋：第一季", "", 100, ""))
            }
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            assertNotNull(model.state.value.detail)
            compose.runOnIdle { model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { !model.state.value.preparing }
            assertNull("备用线路取流失败: ${model.state.value.playbackError}", model.state.value.playbackError)
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertTrue("备用线路未渲染原生画面: ${model.state.value.playbackError}", model.state.value.firstFrameRendered)
        } finally { AppSource.setPlaybackPostForTest(null) }
    }
}
