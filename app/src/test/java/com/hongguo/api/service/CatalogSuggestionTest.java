package com.hongguo.api.service;

import com.hongguo.api.util.Json;
import com.hongguo.api.util.Config;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class CatalogSuggestionTest {
    @BeforeClass public static void initialize() {
        Config.initialize("{\"upstream\":{\"api_host\":\"example.invalid\"}}",
                new java.io.File(System.getProperty("java.io.tmpdir"), "hongguo-catalog-test").getAbsolutePath(),
                "1234567890123456");
    }
    @Test public void parsesSeasonsEvenWhenVideoMetadataIsMissing() {
        String body = "{\"suggest_list\":["
                + "{\"word_type\":\"short_play_name\",\"keyword\":\"123\",\"name\":\"第一季\",\"video_data\":{}},"
                + "{\"word_type\":\"short_play_name\",\"keyword\":\"123\",\"name\":\"重复\"},"
                + "{\"word_type\":\"short_play_name\",\"keyword\":\"bad\",\"name\":\"无效\"},"
                + "{\"word_type\":\"common_query\",\"keyword\":\"456\",\"name\":\"搜索词\"}]}";
        List<Map<String, Object>> result = WebSource.parseSuggestions(body);
        assertEquals(1, result.size());
        assertEquals("123", result.get(0).get("series_id"));
        assertEquals("第一季", result.get(0).get("series_title"));
    }

    @Test public void mergesFirstPageAndRemovesDuplicatesOnLaterPages() {
        List<Object> suggestions = Arrays.asList(row("1"), row("2"));
        Client.Page first = new Client.Page(Arrays.asList(row("1"), row("3")), true, 2);
        Client.Page merged = Client.mergeSearchSuggestions(first, suggestions, 0);
        assertEquals(Arrays.asList("1", "2", "3"), ids(merged.items));
        assertEquals(2, merged.nextSkip);
        assertTrue(merged.hasMore);
        Client.Page next = new Client.Page(Arrays.asList(row("2"), row("4")), false, 4);
        Client.Page second = Client.mergeSearchSuggestions(next, suggestions, 2);
        assertEquals(Arrays.asList("4"), ids(second.items));
        assertEquals(4, second.nextSkip);
        assertFalse(second.hasMore);
    }

    private static Map<String, Object> row(String id) {
        Map<String, Object> row = Json.obj(); row.put("series_id", id); return row;
    }
    private static List<String> ids(List<Object> rows) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Object row : rows) out.add(String.valueOf(Json.optObj(row).get("series_id")));
        return out;
    }
}
