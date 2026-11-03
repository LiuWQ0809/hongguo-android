package com.hongguo.android

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelatedWorksDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun macRelatedSeriesOpensCorrectSecondSeason() {
        lateinit var model: HongguoViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
            model.setThemeStyle(AppThemeStyle.WARM)
            model.openDrama(DramaCard("7671966197985856536", "持械入宋：第一季", "", 100, ""))
        }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        assertNotNull(model.state.value.detail)
        compose.onNodeWithTag("episode_list").performScrollToNode(hasTestTag("related_load"))
        compose.onNodeWithTag("related_load").performClick()
        val second = "related_7676706788724460568"
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(second).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("related_7683455900727659545").assertExists()
        compose.onNodeWithTag("related_7699796892317649944").assertExists()
        compose.onNodeWithTag(second).performScrollTo().performClick()
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        assertEquals("7676706788724460568", model.state.value.detail?.id)
        assertEquals(100, model.state.value.detail?.episodes?.size)
    }
}
