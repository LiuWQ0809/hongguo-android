package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class NativeSeriesDetail {
    private NativeSeriesDetail() { }

    static JSONObject fetch(String seriesId) throws Exception {
        if (!OfficialApiClient.validID(seriesId)) throw new IOException("剧集编号无效");
        JSONObject settings = new JSONObject().put("detail_page_version", 0).put("source", 7)
            .put("disable_digg_stat", false).put("disable_video_relate_book", false)
            .put("need_all_video_definition", false).put("need_mp4_align", false)
            .put("use_os_player", false).put("use_server_dns", false);
        JSONObject body = new JSONObject().put("dr_scene", "preload").put("series_id", seriesId).put("biz_param", settings);
        try (OfficialApiClient client = new OfficialApiClient(OfficialApiClient.newSession())) {
            client.deadlineNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(14));
            JSONObject response = client.cloud("/novel/player/multi_video_detail/preload/v1", "POST", null, body, false);
            return project(seriesId, seriesResponse(seriesId, response));
        }
    }

    static JSONObject seriesResponse(String seriesId, JSONObject response) throws IOException {
        if (response.optInt("code", -1) != 0) throw new IOException("红果 App 目录暂未返回该剧");
        JSONObject data = response.optJSONObject("data");
        JSONObject item = data == null ? null : data.optJSONObject(seriesId);
        JSONObject series = item == null ? null : item.optJSONObject("video_data");
        if (series == null || !seriesId.equals(series.optString("series_id")) || series.optJSONArray("video_list") == null)
            throw new IOException("红果 App 分集目录校验未通过");
        return series;
    }

    private static JSONObject project(String seriesId, JSONObject series) throws Exception {
        JSONArray list = series.getJSONArray("video_list"), episodes = new JSONArray();
        if (list.length() > 1000) throw new IOException("红果 App 分集目录超过上限");
        TreeMap<Integer, JSONObject> ordered = new TreeMap<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject row = list.optJSONObject(i);
            if (row == null) continue;
            String vid = row.optString("vid"), rawIndex = row.optString("vid_index");
            if (!OfficialApiClient.validID(vid) || !rawIndex.matches("[1-9][0-9]{0,3}|10000")) continue;
            int index = Integer.parseInt(rawIndex);
            if (!ordered.containsKey(index) && ids.add(vid)) ordered.put(index, new JSONObject().put("index", index)
                .put("vid", vid).put("availability", "unknown").put("webAvailable", false));
        }
        for (JSONObject row : ordered.values()) episodes.put(row);
        if (ordered.isEmpty()) throw new IOException("红果 App 暂未返回可用的原生分集编号");
        String title = clip(series.optString("series_title", series.optString("series_name")), 200);
        int total = Math.min(10000, Math.max(ordered.lastKey(), series.optInt("episode_cnt")));
        return LibraryStore.series(new JSONObject().put("seriesId", seriesId).put("title", title)
            .put("coverUrl", EpisodeResolver.publicHttpsUrl(series.optString("series_cover")))
            .put("description", clip(series.optString("series_intro"), 8000)).put("episodeCount", total)
            .put("episodeLabel", clip(series.optString("episode_right_text"), 80))).put("episodes", episodes);
    }

    private static String clip(String text, int limit) {
        String trimmed = text.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), limit));
    }
}
