package com.hongguo.api.service

import com.hongguo.api.util.Http
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PlaybackTransportTest {
    @Test fun emptyCompressedResponseIsReadableAndRetryAfterStops() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Encoding", "gzip"))
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Retry-After", "30"))
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30"))
            server.start()
            fun read() = Http.sendPlaybackMetadata(Http.Req(server.url("/").toString()), Long.MAX_VALUE)
            val empty = read()
            assertEquals(0, empty.body.size)
            AppSource.checkPlaybackResponse(empty)
            repeat(2) {
                try { AppSource.checkPlaybackResponse(read()); fail("必须遵守服务端重试提示") }
                catch (expected: AppSource.PlaybackRejectedException) { assertTrue(expected.backoff) }
            }
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun authenticationAndRetryAfterStopRecovery() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Retry-After", "10").setBody("{}"))
            server.start()
            fun read() = Http.sendPlaybackMetadata(Http.Req(server.url("/").toString()), Long.MAX_VALUE)
            try { AppSource.checkPlaybackResponse(read()); fail("401 必须停止取源") }
            catch (expected: AppSource.PlaybackRejectedException) { assertFalse(expected.backoff) }
            try { AppSource.checkPlaybackResponse(read()); fail("必须遵守重试提示") }
            catch (expected: AppSource.PlaybackRejectedException) { assertTrue(expected.backoff) }
            assertEquals(2, server.requestCount)
        }
    }

}
