package io.github.jackotom.hongguo.android;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONObject;

final class CalendarEntry {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private CalendarEntry() { }

    static JSONObject project(JSONObject raw, String selectedDate) throws Exception {
        if (raw == null || !(raw.opt("is_online") instanceof Boolean)) return null;
        boolean online = raw.getBoolean("is_online");
        JSONObject video = online ? raw.optJSONObject("subscribe_data") : raw;
        if (video == null) return null;
        String id = online ? video.optString("series_id") : raw.optString("item_id");
        String title = online ? video.optString("series_title", video.optString("title")) : raw.optString("name");
        if (!OfficialApiClient.validID(id) || title.trim().isEmpty() || title.length() > 200) return null;
        long epoch = raw.optLong("schedule_publish_time", raw.optLong("expected_publish_time"));
        if (epoch <= 0) return null;
        long millis = epoch < 1_000_000_000_000L ? epoch * 1000 : epoch;
        LocalDate date = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
        if (!selectedDate.equals(date.format(DateTimeFormatter.BASIC_ISO_DATE))) return null;
        String expected = online ? "" : "预计" + date.format(DateTimeFormatter.ofPattern("M月d日")) + "上线";
        return new JSONObject().put("seriesId", id).put("title", title.trim())
            .put("coverUrl", EpisodeResolver.publicHttpsUrl(video.optString("series_cover", video.optString("cover"))))
            .put("description", LibraryStore.text(video, online ? "series_intro" : "item_desc", 1000))
            .put("episodeCount", Math.max(0, Math.min(10000, video.optInt("episode_cnt"))))
            .put("upcoming", !online).put("expected", expected);
    }
}
