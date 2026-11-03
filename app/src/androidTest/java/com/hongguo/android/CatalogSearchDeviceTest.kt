package com.hongguo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hongguo.api.service.Client
import com.hongguo.api.util.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogSearchDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchFindsTheSeriesShownOnMacAcrossPages() {
        val ids = linkedSetOf<String>()
        var offset = 0
        for (pageIndex in 0 until 6) {
            val page = Client.searchPage("持械入宋", 20, offset)
            page.items.forEach { raw ->
                val row = Json.optObj(raw) ?: return@forEach
                val id = Json.optStr(row["series_id"], "")
                assertTrue("搜索结果不能跨页重复: $id", ids.add(id))
                println("CATALOG_SAMPLE $id ${Json.optStr(row["title"], "")}")
            }
            if (!page.hasMore) break
            assertTrue("搜索游标必须前进", page.nextSkip > offset)
            offset = page.nextSkip
        }
        assertTrue("未找到 Mac 当前播放的第一季", ids.contains("7671966197985856536"))
    }
}
