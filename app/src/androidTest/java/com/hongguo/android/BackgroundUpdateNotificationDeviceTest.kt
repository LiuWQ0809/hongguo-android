package com.hongguo.android
import android.Manifest
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundUpdateNotificationDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)
    @Test fun backgroundJobNotifiesAboutPublishedUpdate() {
        assumeTrue("仅在更新测试专用旧版本运行", BuildConfig.VERSION_CODE == 37)
        val app = compose.activity.application
        val preferences = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        val oldAutomatic = preferences.getBoolean("automatic_checks", true)
        val oldSource = preferences.getString("source", null)
        val hadNotified = preferences.contains("notified_version")
        val oldNotified = preferences.getLong("notified_version", 0)
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            manager.cancel(UpdateNotifications.NOTIFICATION_ID)
            preferences.edit().putBoolean("automatic_checks", true).putString("source", UpdateSource.PROXY_PRIMARY.name)
                .remove("notified_version").commit()
            UpdateSchedule.sync(app, true)
            assertNotNull(app.getSystemService(JobScheduler::class.java).getPendingJob(UpdateSchedule.JOB_ID))
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            automation.executeShellCommand("cmd jobscheduler run -f com.hongguo.android 2308").close()
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.id == UpdateNotifications.NOTIFICATION_ID }) Thread.sleep(250)
            val notification = manager.activeNotifications.firstOrNull { it.id == UpdateNotifications.NOTIFICATION_ID }
            assertNotNull("后台检查必须产生更新通知", notification)
            assertTrue(preferences.getLong("notified_version", 0) > BuildConfig.VERSION_CODE)
        } finally {
            manager.cancel(UpdateNotifications.NOTIFICATION_ID)
            val edit = preferences.edit().putBoolean("automatic_checks", oldAutomatic)
            if (oldSource == null) edit.remove("source") else edit.putString("source", oldSource)
            if (hadNotified) edit.putLong("notified_version", oldNotified) else edit.remove("notified_version")
            edit.commit()
            UpdateSchedule.sync(app, oldAutomatic)
        }
    }
}
