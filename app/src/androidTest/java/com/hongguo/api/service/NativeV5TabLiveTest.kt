package com.hongguo.api.service

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hongguo.android.DramaCard
import com.hongguo.android.MainActivity
import com.hongguo.android.PlaybackSessionService
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NativeV5TabLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(effectContext = Dispatchers.Main.immediate)
    @Test fun reportedEpisodesRenderViaV5TabAlone() {
        AppSource.setSinglePlaybackForTest { _, _, _ -> throw IOException("旧线路已禁用") }
        AppSource.setPlaybackTabV5ForTest(null)
        AppSource.setPlaybackPostForTest { _, _, _ -> throw IOException("旧线路已禁用") }
        AppSource.setPlaybackTabForTest { _, _ -> throw IOException("旧线路已禁用") }
        val model = compose.activity.playbackModel
        try {
            compose.runOnIdle { model.setPreloadNext(false); model.openDrama(DramaCard("7706825646403488793", "减肥系男神，减一斤一个亿", "", 0, "")) }
            compose.waitUntil(120000) { !model.state.value.loadingDetail }
            val episodes = requireNotNull(model.state.value.detail?.episodes)
            assertTrue(episodes.size >= 2)
            for (index in 0..1) {
                compose.runOnIdle { model.playEpisode(index, restart = true) }
                compose.waitUntil(120000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
                assertNull("第${index + 1}集", model.state.value.playbackError)
                assertTrue(model.state.value.firstFrameRendered)
                val instrument = InstrumentationRegistry.getInstrumentation()
                var start = 0L; instrument.runOnMainSync { start = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }
                compose.waitUntil(30000) { var current = 0L; instrument.runOnMainSync { current = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }; current > start + 1200 }
                println("V5_TAB_PASS episode=${episodes[index].id}")
            }
        } finally {
            AppSource.setPlaybackPostForTest(null); AppSource.setPlaybackTabForTest(null)
            AppSource.setSinglePlaybackForTest(null); AppSource.setPlaybackTabV5ForTest(null)
            compose.runOnIdle { model.closePlayer() }
        }
    }
}
