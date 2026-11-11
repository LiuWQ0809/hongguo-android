package com.hongguo.api.service;

import com.hongguo.api.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SinglePlaybackResponseTest {
    @Test public void acceptsJsonObjectAndEncodedModelForRequestedEpisode() {
        String model = "{\"video_id\":\"v1\",\"video_list\":[]}";
        for (Object value : new Object[]{Json.parseObject(model), model,
                Base64.getEncoder().encodeToString(model.getBytes(StandardCharsets.UTF_8))}) {
            Map<String, Object> root = Json.obj(), data = Json.obj();
            root.put("code", 0); data.put("video_model", value); root.put("data", data);
            Map<String, Object> result = AppSource.parseSingleResponse("123", Json.stringify(root));
            Map<String, Object> episodes = Json.optObj(result.get("data"));
            assertTrue(episodes.containsKey("123"));
            assertEquals("v1", Json.optObj(Json.optObj(episodes.get("123")).get("video_model")).get("video_id"));
        }
    }
    @Test(expected = IllegalStateException.class) public void rejectsEmptyModel() {
        AppSource.parseSingleResponse("123", "{\"code\":0,\"data\":{}}");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidEpisodeId() {
        AppSource.parseSingleResponse("not-an-id", "{\"code\":0,\"data\":{}}");
    }
}
