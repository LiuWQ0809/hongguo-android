// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.account.protocol

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CloudFavoritesTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val replies = mutableMapOf<String, String>()
    private val credentials = AccountCredentials("sessionid=private-fixture", "7100000000000000001", "7200000000000000001")
    private val list = "/reading/bookapi/bookshelf/video/list/v1/"
    private val info = "/reading/bookapi/bookshelf/video/info/v1/"
    private val detail = "/reading/bookapi/multi-detail/v1/"

    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setBody(replies[request.requestUrl!!.encodedPath] ?: """{"code":0,"data":null}""")
        }
        server.start()
    }
    @After fun stop() { server.shutdown(); http.dispatcher.executorService.shutdownNow(); http.connectionPool.evictAll() }
    private fun client() = CloudAccountClient(http, server.url("/"), server.url("/service/2/device_register/"))
    private fun paths() = (0 until server.requestCount).map { server.takeRequest().requestUrl!!.encodedPath }

    @Test fun lightListPreservesLargeIdsAndUsesDetails() {
        replies[list] = """{"code":0,"data":{"video_shelf_info":[{"series_id":7702763145030290457,"collect_time":1762000000}]}}"""
        replies[detail] = """{"code":0,"data":[{"book_id":"7702763145030290457","book_name":"甲","episode_cnt":80}]}"""
        val row = client().favorites(credentials).single()
        assertEquals("7702763145030290457", row.series.id); assertEquals("甲", row.series.title)
        assertEquals("全 80 集", row.series.badge); assertEquals(1762000000000L, row.collectedAt)
        assertEquals(listOf(list, detail), paths())
    }

    @Test fun emptyLightListFallsBackToLegacy() {
        replies[list] = """{"code":0,"data":{"video_shelf_info":[]}}"""
        replies[info] = """{"code":0,"data":{"video_shelf_info":[{"video_data":{"series_id":"701","title":"甲"},"collect_time":5}]}}"""
        assertEquals(listOf("701"), client().favorites(credentials).map { it.series.id })
        assertEquals(listOf(list, info), paths())
    }

    @Test fun failedLightListUsesLegacyWithoutMaskingInvalidData() {
        replies[list] = """{"code":4}"""
        replies[info] = """{"code":0,"data":null}"""
        assertThrows(java.io.IOException::class.java) { client().favorites(credentials) }
        assertEquals(listOf(list, info), paths())
    }

    @Test fun expiredLoginDoesNotFallBack() {
        replies[list] = """{"code":1003}"""
        val error = assertThrows(AccountProtocolException::class.java) { client().favorites(credentials) }
        assertTrue(error.requiresLogin); assertEquals(listOf(list), paths())
    }

    @Test fun detailFailurePreservesIdentifiers() {
        replies[list] = """{"code":0,"data":{"video_shelf_info":[{"series_id":"701","collect_time":1}]}}"""
        replies[detail] = """{"code":4}"""
        val row = client().favorites(credentials).single()
        assertEquals("701", row.series.id); assertEquals("剧集 701", row.series.title)
    }
}
