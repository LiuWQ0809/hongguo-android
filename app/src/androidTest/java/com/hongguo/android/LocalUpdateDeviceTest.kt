package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalUpdateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun olderBootstrapFindsAndVerifiesPublishedUpdate() {
        assumeTrue("仅在更新测试专用旧版本运行", BuildConfig.VERSION_CODE < 39)
        lateinit var model: UpdateViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[UpdateViewModel::class.java]
            model.selectSource(UpdateSource.PROXY_PRIMARY)
            model.checkForUpdates()
        }
        compose.waitUntil(90_000) { !model.state.value.checking && (model.state.value.update != null || model.state.value.error != null) }
        assertNull("检查自有更新源失败", model.state.value.error)
        assertTrue("发布版本必须高于测试版本", model.state.value.update!!.versionCode > BuildConfig.VERSION_CODE)
        assertTrue(model.state.value.update!!.apkUrl.contains("github.com/LiuWQ0809/hongguo-android/"))
        compose.runOnIdle { model.downloadUpdate() }
        compose.waitUntil(240_000) { !model.state.value.downloading }
        assertNull("下载或校验自有更新失败", model.state.value.error)
        assertNotNull("更新包必须通过包名、版本、哈希和签名验证", model.state.value.downloadedFile)
    }
}
