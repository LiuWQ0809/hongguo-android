package com.hongguo.android

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SearchUsabilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recentSearchCanBeReusedAndCleared() {
        val preferences = compose.activity.getSharedPreferences(ThemePreferences.FILE, Context.MODE_PRIVATE)
        val oldHistory = preferences.getString(RecentSearches.KEY, null)
        val oldQuery = preferences.getString("last_query", null)
        lateinit var model: HongguoViewModel
        try {
            compose.runOnIdle {
                model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java]
                model.clearRecentSearches()
                model.selectTab(AppTab.SEARCH)
            }
            compose.onNodeWithTag("search_input").performTextClearance()
            compose.onNodeWithTag("search_input").performTextInput("持械入宋")
            compose.onNodeWithTag("search_button").performClick()
            compose.waitUntil(120_000) { !model.state.value.searching }
            assertNull(model.state.value.searchError)
            assertTrue(model.state.value.results.isNotEmpty())
            if (model.state.value.hasMore) {
                val previousSize = model.state.value.results.size
                compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("result_${previousSize - 1}"))
                compose.waitUntil(120_000) { model.state.value.results.size > previousSize || model.state.value.searchError != null }
                assertNull("Scrolling must load the next page", model.state.value.searchError)
                assertTrue(model.state.value.results.size > previousSize)
                assertEquals(model.state.value.results.size, model.state.value.results.distinctBy { it.id }.size)
            }
            compose.onNodeWithTag("search_clear").performClick()
            compose.onNodeWithTag("recent_search_0").performClick()
            compose.waitUntil(120_000) { !model.state.value.searching }
            assertEquals("持械入宋", model.state.value.searchedQuery)
            compose.onNodeWithTag("search_clear").performClick()
            compose.onNodeWithTag("recent_search_clear").performClick()
            assertTrue(model.state.value.recentSearches.isEmpty())
        } finally {
            val editor = preferences.edit()
            if (oldHistory == null) editor.remove(RecentSearches.KEY) else editor.putString(RecentSearches.KEY, oldHistory)
            if (oldQuery == null) editor.remove("last_query") else editor.putString("last_query", oldQuery)
            editor.commit()
        }
    }
}
