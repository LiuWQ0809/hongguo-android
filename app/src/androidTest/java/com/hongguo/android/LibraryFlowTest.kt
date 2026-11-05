package com.hongguo.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun continueWatchingIsReachableInEveryHomeStyle() {
        val context = compose.activity.applicationContext
        val preferences = context.getSharedPreferences("hongguo_library", Context.MODE_PRIVATE)
        val original = preferences.getString("snapshot", null)
        val store = LibraryStore(context)
        lateinit var model: HongguoViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java] }
        val style = model.state.value.themeStyle
        val dark = model.state.value.darkFollowsSystem
        try {
            store.recordPlayback(DramaCard("7696083096164125721", "铁血信使", "", 75, ""),
                "7696085244503411774", 1, 8_000)
            compose.waitUntil(5_000) { model.state.value.history.firstOrNull()?.card?.id == "7696083096164125721" }
            compose.runOnIdle { model.setDarkFollowsSystem(false); model.setThemeStyle(AppThemeStyle.WARM) }
            compose.onNodeWithTag("home_continue_play").assertExists()
            compose.runOnIdle { model.setThemeStyle(AppThemeStyle.CARDS) }
            compose.onNodeWithTag("cards_continue").assertExists()
            compose.runOnIdle { model.setThemeStyle(AppThemeStyle.IMMERSIVE) }
            compose.onNodeWithTag("immersive_continue_play").performClick()
            compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
            assertNull("Continue must load the saved episode", model.state.value.playbackError)
            assertEquals("7696085244503411774", model.state.value.video?.episodeId)
            assertTrue("Continue must restore the saved position", model.state.value.resumePositionMs >= 7_000)
        } finally {
            compose.runOnIdle { model.closePlayer(); model.setThemeStyle(style); model.setDarkFollowsSystem(dark) }
            val editor = preferences.edit()
            if (original == null) editor.remove("snapshot") else editor.putString("snapshot", original)
            assertTrue(editor.commit())
            LibraryStore(context)
        }
    }

    @Test fun localListsPersistAndClearIndependentlyThroughUi() {
        val context = compose.activity.applicationContext
        val preferences = context.getSharedPreferences("hongguo_library", Context.MODE_PRIVATE)
        val original = preferences.getString("snapshot", null)
        val store = LibraryStore(context)
        lateinit var model: HongguoViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[HongguoViewModel::class.java] }
        val card = DramaCard("18446744073709551615", "本地数据验证", "", 10, "")
        val second = DramaCard("18446744073709551614", "收藏删除验证", "", 8, "")
        try {
            store.clearFavorites(); store.clearHistory()
            store.recordPlayback(card, "18446744073709551613", 3, 12_345)
            store.toggleFavorite(card); store.toggleFavorite(second)
            // Force pending preference changes to disk before constructing another store.
            assertTrue(preferences.edit().commit())
            val reloaded = LibraryStore(context).state.value
            assertEquals(2, reloaded.favorites.size)
            assertEquals(card.id, reloaded.history.single().card.id)
            assertEquals(12_345L, reloaded.history.single().positionMs)
            compose.onNodeWithTag("nav_favorites").performClick()
            compose.waitUntil(5_000) { model.state.value.favorites.size == 2 }
            compose.onNodeWithTag("library_source_account").performClick()
            compose.runOnIdle {
                assertTrue(model.state.value.libraryCloud)
                assertEquals(2, model.state.value.favorites.size)
                assertEquals(1, model.state.value.history.size)
            }
            compose.onNodeWithTag("library_source_local").performClick()
            compose.onNodeWithTag("library_favorites").performClick()
            compose.onNodeWithTag("favorite_item_0").assertExists()
            compose.onNodeWithTag("library_manage").performClick()
            compose.onNodeWithTag("favorite_remove_0").performClick()
            compose.waitUntil(5_000) { model.state.value.favorites.size == 1 }
            assertEquals(1, model.state.value.history.size)
            compose.onNodeWithTag("clear_favorites").performClick()
            compose.onNodeWithText("取消").performClick()
            assertEquals(1, model.state.value.favorites.size)
            compose.onNodeWithTag("clear_favorites").performClick()
            compose.onNodeWithTag("confirm_clear_favorites").performClick()
            compose.waitUntil(5_000) { model.state.value.favorites.isEmpty() }
            assertEquals(1, model.state.value.history.size)
            compose.onNodeWithTag("nav_mine").performClick()
            compose.onNodeWithTag("my_history").performScrollTo().performClick()
            compose.onNodeWithTag("history_item_0").assertExists()
            compose.onNodeWithTag("library_manage").performClick()
            compose.onNodeWithTag("history_remove_0").performClick()
            compose.waitUntil(5_000) { model.state.value.history.isEmpty() }
            store.recordPlayback(card, "18446744073709551613", 3, 12_345)
            store.toggleFavorite(card)
            compose.waitUntil(5_000) { model.state.value.history.size == 1 }
            compose.onNodeWithTag("clear_history").performClick()
            compose.onNodeWithTag("confirm_clear_history").performClick()
            compose.waitUntil(5_000) { model.state.value.history.isEmpty() }
            assertEquals(1, model.state.value.favorites.size)
            store.updatePosition(card, "18446744073709551613", 3, 14_000)
            assertTrue(store.state.value.history.isEmpty())
            assertEquals(14_000L, store.state.value.favorites.single().positionMs)
        } finally {
            val editor = preferences.edit()
            if (original == null) editor.remove("snapshot") else editor.putString("snapshot", original)
            assertTrue(editor.commit())
            LibraryStore(context) // Synchronize shared observable state with the restored disk snapshot.
        }
    }
}
