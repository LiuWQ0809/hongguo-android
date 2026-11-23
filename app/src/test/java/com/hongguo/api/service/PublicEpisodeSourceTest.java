package com.hongguo.api.service;

import org.junit.Test;
import com.hongguo.api.util.Json;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class PublicEpisodeSourceTest {
    private static final String CDN = "https://v1.qznovelvod.com/video.mp4";

    private static String reply(String url, long expires, int code) {
        Map<String, Object> payload = Json.obj();
        payload.put("base_resp", Collections.singletonMap("StatusCode", code));
        payload.put("main_url", url);
        payload.put("url_expire", expires);
        return Json.stringify(payload);
    }

    @Test public void completeMediaUrlProducesAnUnencryptedTrack() throws Exception {
        Stream.VideoTrack track = PublicEpisodeSource.parse(reply(CDN, 120, 0), 100);
        assertEquals(CDN, track.url);
        assertFalse(track.encrypt);
        assertEquals("360p", track.definition);
    }

    @Test public void rejectsExpiredRefusedOrUntrustedSources() {
        for (String invalid : new String[] { reply(CDN, 105, 0), reply(CDN, 120, 1),
                reply("http://v1.qznovelvod.com/video.mp4", 120, 0),
                reply("https://qznovelvod.com.example.invalid/video.mp4", 120, 0),
                reply(CDN + "#fragment", 120, 0), "{\"main_url\":\"" + CDN + "\"}" }) {
            assertThrows(Exception.class, () -> PublicEpisodeSource.parse(invalid, 100));
        }
    }
}
