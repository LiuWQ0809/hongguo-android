package com.hongguo.android

import android.content.res.Configuration
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun startVideo(): HongguoViewModel {
        lateinit var model: HongguoViewModel
        compose.runOnIdle {
            model = compose.activity.playbackModel
            model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, ""))
        }
        compose.waitUntil(120_000) { !model.state.value.loadingDetail }
        assertNotNull(model.state.value.detail)
        compose.runOnIdle { model.playEpisode(0, restart = true) }
        compose.waitUntil(120_000) { model.state.value.firstFrameRendered || model.state.value.playbackError != null }
        assertNull(model.state.value.playbackError)
        assertTrue(model.state.value.firstFrameRendered)
        return model
    }

    @Test fun fullscreenRotatesAndReturnsWithoutRestartingVideo() {
        val model = startVideo()
        val request = model.state.value.video?.requestId
        compose.onNodeWithTag("player_fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertEquals(request, model.state.value.video?.requestId)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("player_top_bar").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player_seek").assertDoesNotExist()
        compose.onNodeWithTag("player_toggle").performClick()
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.runOnIdle { assertTrue("Showing controls must not pause playback", findPlayer(compose.activity.window.decorView)?.playWhenReady == true) }
        Thread.sleep(3_000)
        compose.onNodeWithTag("player_seek").performTouchInput { click(center) }
        Thread.sleep(3_000)
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.onNodeWithTag("player_seek").performTouchInput {
            down(Offset(width * .35f, center.y))
            moveTo(Offset(width * .5f, center.y), delayMillis = 200)
        }
        Thread.sleep(5_500)
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.onNodeWithTag("player_seek").performTouchInput { up() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("player_pause").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_pause").performClick()
        compose.onNodeWithTag("player_resume").assertExists()
        Thread.sleep(5_500)
        compose.onNodeWithTag("player_top_bar").assertExists()
        compose.onNodeWithTag("player_resume").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("player_top_bar").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player_toggle").performClick()
        compose.onNodeWithTag("player_fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertEquals(request, model.state.value.video?.requestId)
    }

    private fun findPlayer(view: View): Player? {
        if (view is PlayerView) return view.player
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun position(player: Player): Long {
        var value = -1L
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value = player.currentPosition }
        return value
    }

    private fun assertBackgroundEpisodes(model: HongguoViewModel, instrumentation: android.app.Instrumentation) {
        var previous: Player? = null
        repeat(3) { index ->
            val deadline = System.currentTimeMillis() + 120_000
            var active: Player? = null
            while (System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    active = PlaybackSessionService.activePlayer?.takeIf { it !== previous && it.isPlaying && it.duration > 5_000 }
                }
                if (active != null) break
                Thread.sleep(200)
            }
            val playing = requireNotNull(active) { "Background player must survive activity destruction" }
            assertNotSame(previous, playing)
            val start = position(playing)
            Thread.sleep(3_000)
            assertTrue("The episode must actually advance", position(playing) > start + 1_000)
            assertEquals(index, model.state.value.episodePosition)
            if (index < 2) {
                instrumentation.runOnMainSync { playing.seekTo(playing.duration - 1_000) }
                while (System.currentTimeMillis() < deadline && model.state.value.episodePosition <= index) Thread.sleep(200)
                assertEquals(index + 1, model.state.value.episodePosition)
            }
            previous = playing
        }
    }

    @Test fun playbackContinuesAfterBackgroundActivityIsRecreated() = checkDestroyedActivityPlayback(false)
    @Test fun lockScreenPlaysAfterBackgroundActivityIsRecreated() = checkDestroyedActivityPlayback(true)

    @Test fun returningAfterRecreationPreservesBackgroundPauseAndPosition() {
        val model = startVideo()
        val activity = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        try {
            instrumentation.runOnMainSync { activity.updatePictureInPicture(false, 0, 0) }
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            val deadline = System.currentTimeMillis() + 30_000
            while (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && System.currentTimeMillis() < deadline) Thread.sleep(100)
            instrumentation.runOnMainSync { activity.recreate() }
            while (!activity.isDestroyed && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue(activity.isDestroyed)
            var background: Player? = null
            while (background == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { background = PlaybackSessionService.activePlayer?.takeIf { it.isPlaying && it.duration > 15_000 } }
                if (background == null) Thread.sleep(200)
            }
            val playing = requireNotNull(background)
            instrumentation.runOnMainSync { playing.seekTo(10_000); playing.pause() }
            automation.executeShellCommand("am start -n com.hongguo.android/.MainActivity -f 0x20000000").close()
            var foreground: Player? = null
            while (foreground == null && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { foreground = PlaybackSessionService.activePlayer?.takeIf { it !== playing } }
                if (foreground == null) Thread.sleep(200)
            }
            val restored = requireNotNull(foreground)
            instrumentation.runOnMainSync {
                assertFalse("Background pause must survive returning to the player", restored.playWhenReady)
                assertTrue("Background position must survive returning to the player", restored.currentPosition >= 9_000)
            }
        } finally {
            instrumentation.runOnMainSync { model.closePlayer() }
        }
    }

    private fun checkDestroyedActivityPlayback(locked: Boolean) {
        val model = startVideo()
        val activity = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        try {
            instrumentation.runOnMainSync { model.setAutoPlayNext(true); activity.updatePictureInPicture(false, 0, 0) }
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            val stopDeadline = System.currentTimeMillis() + 10_000
            while (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && System.currentTimeMillis() < stopDeadline) Thread.sleep(100)
            assertFalse(activity.isInPictureInPictureMode)
            instrumentation.runOnMainSync { activity.recreate() }
            val destroyDeadline = System.currentTimeMillis() + 20_000
            while (!activity.isDestroyed && System.currentTimeMillis() < destroyDeadline) Thread.sleep(100)
            assertTrue("The stopped activity must be destroyed: state=${activity.lifecycle.currentState}", activity.isDestroyed)
            if (locked) {
                automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
                Thread.sleep(1_500)
                assertFalse(activity.getSystemService(PowerManager::class.java).isInteractive)
                assertTrue(activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            }
            assertBackgroundEpisodes(model, instrumentation)
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }

    @Test fun backgroundStartsPreparedEpisodeWhenNoPlayerExists() {
        val application = compose.activity.application
        val store = ViewModelStore()
        val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[HongguoViewModel::class.java]
        var owner: BackgroundPlayback? = null
        try {
            compose.runOnIdle { model.openDrama(DramaCard("7696083096164125721", "铁血信使", "", 0, "")) }
            compose.waitUntil(120_000) { !model.state.value.loadingDetail }
            assertNotNull(model.state.value.detail)
            compose.runOnIdle { model.playEpisode(0, restart = true) }
            compose.waitUntil(120_000) { model.state.value.video != null || model.state.value.playbackError != null }
            assertNull(model.state.value.playbackError)
            val video = requireNotNull(model.state.value.video)
            compose.runOnIdle {
                owner = BackgroundPlayback(application, model, video, null, initialPlayWhenReady = false) {}
                val preparedPlayer = requireNotNull(owner?.current)
                assertFalse("Background takeover must preserve pause intent", preparedPlayer.playWhenReady)
                preparedPlayer.play()
            }
            compose.waitUntil(30_000) {
                var playing = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = owner?.current?.isPlaying == true }
                playing
            }
            val active = requireNotNull(owner?.current)
            val start = position(active)
            Thread.sleep(3_000)
            assertTrue("A prepared episode must advance without a previous player", position(active) > start + 1_000)
        } finally {
            compose.runOnIdle { owner?.stop(); store.clear() }
        }
    }

    @Test fun ordinaryBackgroundPlaysNextEpisodeWithoutPictureInPicture() {
        val model = startVideo()
        val activity = compose.activity
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.updatePictureInPicture(false, 0, 0) }
        automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        val stopDeadline = System.currentTimeMillis() + 10_000
        while (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && System.currentTimeMillis() < stopDeadline)
            Thread.sleep(200)
        assertFalse("The activity must be stopped", activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        assertFalse(activity.isInPictureInPictureMode)
        var player: Player? = null
        val readyDeadline = System.currentTimeMillis() + 30_000
        while (player == null && System.currentTimeMillis() < readyDeadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player = PlaybackSessionService.activePlayer?.takeIf { it.isPlaying && it.duration > 5_000 }
            }
            if (player == null) Thread.sleep(200)
        }
        val active = requireNotNull(player)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { active.seekTo(active.duration - 1_000) }
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline && (model.state.value.episodePosition < 1 || model.state.value.video == null))
            Thread.sleep(300)
        assertEquals("Ordinary background playback must advance", 1, model.state.value.episodePosition)
        var next: Player? = null
        while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { next = PlaybackSessionService.activePlayer }
            Thread.sleep(300)
        }
        val nextPlayer = requireNotNull(next)
        assertNotSame(active, nextPlayer)
        val start = position(nextPlayer)
        Thread.sleep(4_000)
        assertTrue("The next episode must play in the background", position(nextPlayer) > start + 1_000)
    }

    @Test fun playerExposesAutoplaySettingAndNextEpisode() {
        val model = startVideo()
        val initial = model.state.value.autoPlayNext
        try {
            compose.runOnIdle { model.setAutoPlayNext(true) }
            compose.onNodeWithTag("player_episode_picker").performClick()
            compose.onNodeWithTag("player_auto_next").performClick()
            assertFalse("The player sheet must disable autoplay", model.state.value.autoPlayNext)
            compose.onNodeWithTag("player_auto_next").performClick()
            assertTrue("The player sheet must re-enable autoplay", model.state.value.autoPlayNext)
            compose.onNodeWithTag("player_episodes_close").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("player_next").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player_next").performClick()
            compose.waitUntil(120_000) { model.state.value.episodePosition == 1 &&
                (model.state.value.firstFrameRendered || model.state.value.playbackError != null) }
            assertNull("The next button must start the selected episode", model.state.value.playbackError)
            assertTrue(model.state.value.firstFrameRendered)
        } finally {
            compose.runOnIdle { model.setAutoPlayNext(initial) }
        }
    }

    @Test fun homeContinueRestoresEpisodeAndPosition() {
        val model = startVideo()
        val originalStyle = model.state.value.themeStyle
        val episodeId = requireNotNull(model.state.value.video).episodeId
        try {
            compose.runOnIdle {
                model.setThemeStyle(AppThemeStyle.WARM)
                requireNotNull(findPlayer(compose.activity.window.decorView)).seekTo(8_000)
            }
            compose.waitUntil(30_000) { model.state.value.history.firstOrNull()?.positionMs?.let { it >= 7_000 } == true }
            compose.runOnIdle { model.closePlayer(); model.backToSearch(); model.selectTab(AppTab.HOME) }
            compose.onNodeWithTag("home_continue_play").performClick()
            compose.waitUntil(120_000) { model.state.value.video != null &&
                (model.state.value.firstFrameRendered || model.state.value.playbackError != null) }
            assertNull(model.state.value.playbackError)
            assertEquals("Continue must restore the saved episode", episodeId, model.state.value.video?.episodeId)
            assertTrue("Continue must restore the saved position", model.state.value.resumePositionMs >= 7_000)
        } finally {
            compose.runOnIdle { model.setThemeStyle(originalStyle) }
        }
    }

    @Test fun backgroundContinuesAfterActivityRecreation() {
        val model = startVideo()
        compose.activityRule.scenario.recreate()
        val activity = compose.activity
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(30_000) { compose.runOnIdle { findPlayer(activity.window.decorView)?.isPlaying == true } }
        try {
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            compose.waitUntil(10_000) { activity.isInPictureInPictureMode }
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            Thread.sleep(1_000)
            assertTrue(activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            var player: Player? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { player = PlaybackSessionService.activePlayer }
            val active = requireNotNull(player) { "Recreating the activity must not release background playback" }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { active.seekTo(active.duration - 1_000) }
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && (model.state.value.episodePosition < 1 || model.state.value.video == null))
                Thread.sleep(300)
            assertEquals("Playback must advance after activity recreation", 1, model.state.value.episodePosition)
            var next: Player? = null
            while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { next = PlaybackSessionService.activePlayer }
                Thread.sleep(300)
            }
            val nextPlayer = requireNotNull(next)
            assertNotSame(active, nextPlayer)
            val start = position(nextPlayer)
            Thread.sleep(4_000)
            assertTrue("The next episode must keep playing", position(nextPlayer) > start + 1_000)
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }

    @Test fun pictureInPictureAndScreenOffContinuePlayback() {
        val model = startVideo()
        val activity = compose.activity
        var player: Player? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player = findPlayer(activity.window.decorView) }
        var active = requireNotNull(player)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            compose.waitUntil(10_000) { activity.isInPictureInPictureMode }
            Thread.sleep(1_000)
            var expectedAspect = 0f
            var pipAspect = 0f
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                expectedAspect = (active.videoSize.width.toFloat() / active.videoSize.height).coerceIn(1f / 2.39f, 2.39f)
                val view = activity.window.decorView
                pipAspect = view.width.toFloat() / view.height
            }
            assertEquals("PiP must follow the video's aspect ratio", expectedAspect, pipAspect, .12f)
            val pipStart = position(active)
            Thread.sleep(3_000)
            assertTrue("PiP playback must advance", position(active) > pipStart + 1_000)
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            Thread.sleep(1_000)
            assertFalse("The screen must really be off", activity.getSystemService(PowerManager::class.java).isInteractive)
            assertTrue("The lock screen must be active", activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            val handoffDeadline = System.currentTimeMillis() + 20_000
            var backgroundPlayer: Player? = null
            while (backgroundPlayer == null && System.currentTimeMillis() < handoffDeadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    backgroundPlayer = PlaybackSessionService.activePlayer?.takeIf { it.isPlaying }
                }
                if (backgroundPlayer == null) Thread.sleep(200)
            }
            active = requireNotNull(backgroundPlayer) { "Screen-off player must remain active" }
            val lockedStart = position(active)
            Thread.sleep(3_000)
            assertTrue("Screen-off playback must advance", position(active) > lockedStart + 1_000)
            automation.executeShellCommand("input keyevent KEYCODE_MEDIA_PAUSE").close()
            Thread.sleep(500)
            var playing = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = active.playWhenReady }
            assertFalse("Media controls must pause while screen is off", playing)
            automation.executeShellCommand("input keyevent KEYCODE_MEDIA_PLAY").close()
            Thread.sleep(500)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = active.playWhenReady }
            assertTrue("Media controls must resume while screen is off", playing)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(active.duration > 5_000)
                active.seekTo(active.duration - 1_000)
            }
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && (model.state.value.episodePosition < 1 || model.state.value.video == null))
                Thread.sleep(300)
            assertEquals("Screen-off autoplay must advance to the next episode", 1, model.state.value.episodePosition)
            assertNotNull("The next episode must be prepared while screen is off", model.state.value.video)
            assertNull(model.state.value.playbackError)
            var next: Player? = null
            while (System.currentTimeMillis() < deadline && (next == null || next === active)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { next = PlaybackSessionService.activePlayer }
                if (next == null || next === active) Thread.sleep(300)
            }
            val nextPlayer = requireNotNull(next)
            assertNotSame("The next episode must have a new player", active, nextPlayer)
            val nextStart = position(nextPlayer)
            Thread.sleep(4_000)
            assertTrue("The next episode must play while screen is off", position(nextPlayer) > nextStart + 1_000)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { nextPlayer.seekTo(nextPlayer.duration - 1_000) }
            val secondDeadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < secondDeadline && (model.state.value.episodePosition < 2 || model.state.value.video == null))
                Thread.sleep(300)
            assertEquals("A second screen-off transition must advance", 2, model.state.value.episodePosition)
            var current: Player? = null
            while (System.currentTimeMillis() < secondDeadline && (current == null || current === nextPlayer)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { current = PlaybackSessionService.activePlayer }
                if (current == null || current === nextPlayer) Thread.sleep(300)
            }
            val third = requireNotNull(current)
            assertNotSame(nextPlayer, third)
            val thirdStart = position(third)
            Thread.sleep(4_000)
            assertTrue("The third episode must play while screen is off", position(third) > thirdStart + 1_000)
            assertEquals("History must follow actual background playback", 3,
                model.state.value.history.firstOrNull { it.card.id == "7696083096164125721" }?.episodeIndex)
            val detail = requireNotNull(model.state.value.detail)
            val savedSecond = activity.getSharedPreferences(ThemePreferences.FILE, 0)
                .getLong("${detail.id}.${detail.episodes[1].id}.position", -1)
            assertEquals("A completed background episode must reopen at the beginning", 0L, savedSecond)
            val request = model.state.value.video?.requestId
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
            automation.executeShellCommand("am start -n com.hongguo.android/.MainActivity -f 0x20000000").close()
            compose.waitUntil(60_000) { model.state.value.firstFrameRendered }
            assertEquals("Unlocking must keep the same episode", request, model.state.value.video?.requestId)
            compose.runOnIdle { model.closePlayer() }
            compose.waitUntil(10_000) { PlaybackSessionService.activePlayer == null }
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }
}
