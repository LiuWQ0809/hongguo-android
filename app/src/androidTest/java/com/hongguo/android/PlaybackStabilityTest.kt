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
}
