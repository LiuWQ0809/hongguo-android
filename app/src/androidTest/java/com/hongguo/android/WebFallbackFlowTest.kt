package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebFallbackFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nativeStreamOrInAppWebsiteIsAvailable() {
        lateinit var model: HongguoViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java] }
        compose.onNodeWithTag("home_search").performClick()
        compose.onNodeWithTag("search_input").performTextClearance()
        compose.onNodeWithTag("search_input").performTextInput("总裁")
        compose.onNodeWithTag("search_button").performClick()
        compose.waitUntil(120_000) { !model.state.value.searching }
        assertTrue("No search results", model.state.value.results.isNotEmpty())
        compose.onNodeWithTag("result_0").performClick()
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        assertNotNull("No episode details", model.state.value.detail)
        compose.onNodeWithTag("episode_0").performScrollTo().performClick()
        compose.waitUntil(120_000) { !model.state.value.preparing }
        if (model.state.value.playbackError == null) {
            assertNotNull("Native playback should have a prepared video", model.state.value.video)
            return
        }
        compose.onNodeWithTag("web_playback").performClick()
        var opened = false
        compose.waitUntil(15_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                opened = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is WebPlaybackActivity }
            }
            opened
        }
        assertTrue(opened)
    }
}
