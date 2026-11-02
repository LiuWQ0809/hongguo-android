package com.hongguo.android

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebPlaybackActivityTest {
    @Test fun websiteVideoCanAdvanceOnTheDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, WebPlaybackActivity::class.java)
            .putExtra("series", "7696083096164125721")
            .putExtra("episode", "7696085244503411774")
        ActivityScenario.launch<WebPlaybackActivity>(intent).use { scenario ->
            var advanced = false
            var tapped = false
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && !advanced) {
                val latch = CountDownLatch(1)
                var result = ""
                scenario.onActivity { activity ->
                    val browser = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as WebView
                    browser.evaluateJavascript("""(() => { const v = document.querySelector('video');
                        if (!v || v.readyState === 0) return 'loading';
                        return v.currentTime > 1 ? 'playing' : 'ready'; })()""") { result = it; latch.countDown() }
                }
                assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
                advanced = result == "\"playing\""
                if (!tapped && result == "\"ready\"") {
                    val now = SystemClock.uptimeMillis()
                    val automation = InstrumentationRegistry.getInstrumentation()
                    automation.sendPointerSync(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 540f, 600f, 0))
                    automation.sendPointerSync(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 540f, 600f, 0))
                    tapped = true
                }
                if (!advanced) Thread.sleep(1000)
            }
            assertTrue("Website video did not advance on this Android emulator", advanced)
        }
    }

    @Test fun episodeOpensOnTheExpectedSiteWithoutFileAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, WebPlaybackActivity::class.java)
            .putExtra("series", "7696083096164125721")
            .putExtra("episode", "7696085244503411774")
        ActivityScenario.launch<WebPlaybackActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val browser = content.getChildAt(0) as WebView
                assertEquals("https://hongguoduanju.com/player/7696083096164125721/7696085244503411774", browser.url)
                assertTrue(browser.settings.javaScriptEnabled)
                assertFalse(browser.settings.allowFileAccess)
                assertFalse(browser.settings.allowContentAccess)
            }
        }
    }
}
