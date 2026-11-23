package com.hongguo.api.service
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hongguo.android.DramaCard
import com.hongguo.android.MainActivity
import com.hongguo.android.PlaybackSessionService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicEpisodePlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun emptyAppRoutesRecoverCompleteEpisodeAndSeek() {
        AppSource.setSinglePlaybackForTest { _, _, _ -> throw AppSource.EmptyPlaybackResponseException() }
        AppSource.setPlaybackPostForTest { _, _, _ -> throw AppSource.EmptyPlaybackResponseException() }
        AppSource.setPlaybackTabForTest { _, _ -> null }
        val model = compose.activity.playbackModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            compose.runOnIdle {
                model.setPreloadNext(false)
                model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
            }
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            assertEquals("7696085244503411774", model.state.value.detail?.episodes?.firstOrNull()?.id)
            compose.runOnIdle { model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull(model.state.value.playbackError)
            var duration = 0L
            instrumentation.runOnMainSync {
                val player = requireNotNull(PlaybackSessionService.activePlayer)
                duration = player.duration
                assertTrue("完整分集的时长须超过 60 秒", duration > 60_000)
                player.seekTo(30_000)
            }
            compose.waitUntil(30_000) {
                var position = 0L
                instrumentation.runOnMainSync { position = PlaybackSessionService.activePlayer?.currentPosition ?: 0L }
                position > 32_000
            }
            println("PUBLIC_EPISODE_PASS durationMs=$duration seekMs=30000")
        } finally {
            AppSource.setSinglePlaybackForTest(null); AppSource.setPlaybackPostForTest(null); AppSource.setPlaybackTabForTest(null)
            compose.runOnIdle { model.closePlayer() }
        }
    }
}
