package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublisherDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun showsPublisherWhileWatching() {
        val model = compose.activity.playbackModel
        compose.runOnIdle { model.openDrama(DramaCard("7706825646403488793", "减肥系男神，减一斤一个亿", "", 0, "")) }
        compose.waitUntil(60_000) { !model.state.value.loadingDetail }
        compose.runOnIdle { model.playEpisode(0, restart = true) }
        compose.waitUntil(60_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
        assertNull(model.state.value.playbackError)
        assertTrue(model.state.value.firstFrameRendered)
        compose.waitUntil(60_000) { compose.onAllNodesWithText("发布方 · 冰川短剧 ›").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("发布方 · 冰川短剧 ›").performClick()
        compose.onNodeWithTag("publisher_works").assertExists()
    }

    @Test fun showsPublisherAndOpensAnotherVerifiedWork() {
        val model = compose.activity.playbackModel
        compose.runOnIdle { model.openDrama(DramaCard("7706825646403488793", "减肥系男神，减一斤一个亿", "", 0, "")) }
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("drama_publisher").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("发布方 · 冰川短剧 ›").performClick()
        val otherTitle = "反派幼崽六岁半，爹不听话我来干"
        compose.waitUntil(90_000) { compose.onAllNodesWithText(otherTitle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("publisher_works").assertExists()
        compose.onNodeWithText(otherTitle).performClick()
        compose.waitUntil(60_000) { !model.state.value.loadingDetail }
        assertEquals("7566636110479117337", model.state.value.detail?.id)
        println("PUBLISHER_PROBE 冰川短剧作品已打开：$otherTitle")
    }
}
