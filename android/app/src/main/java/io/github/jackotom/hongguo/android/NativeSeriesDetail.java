package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class NativeSeriesDetail {
    private NativeSeriesDetail() { }

    static JSONObject fetch(String seriesId) throws Exception {
        return fetch(seriesId, System.nanoTime() + TimeUnit.SECONDS.toNanos(14));
    }

    static JSONObject fetch(String seriesId, long deadline) throws Exception {
        if (!OfficialApiClient.validID(seriesId)) throw new IOException("剧集编号无效");
        JSONObject settings = new JSONObject().put("detail_page_version", 0).put("source", 7)
            .put("disable_digg_stat", false).put("disable_video_relate_book", false)
            .put("need_all_video_definition", false).put("need_mp4_align", false)
            .put("use_os_player", false).put("use_server_dns", false);
        JSONObject body = new JSONObject().put("dr_scene", "preload").put("series_id", seriesId).put("biz_param", settings);
        try (OfficialApiClient client = new OfficialApiClient(OfficialApiClient.newSession())) {
            return NativeDirectoryFallback.read(deadline, initialDeadline -> {
                client.deadlineNanos(initialDeadline);
                JSONObject response = client.cloud("/novel/player/multi_video_detail/preload/v1", "POST", null, body, false);
                return project(seriesId, seriesResponse(seriesId, response));
            }, alternateDeadline -> {
                client.deadlineNanos(alternateDeadline);
                JSONObject detail = new JSONObject().put("detail_page_version", 0).put("source", 4).put("video_id_type", 1)
                    .put("caller_scene", "single_col").put("from_video_id", "")
                    .put("need_all_video_definition", false).put("need_mp4_align", false);
                JSONObject request = new JSONObject().put("series_id", seriesId).put("biz_param", detail);
                JSONObject response = client.cloud("/novel/player/video_detail/v1/", "POST", null, request, true);
                return project(seriesId, seriesResponse(seriesId, response, true));
            });
        }
    }

    static final class MissingDirectory extends IOException {
        MissingDirectory(String message) { super(message); }
    }

    static JSONObject seriesResponse(String seriesId, JSONObject response) throws IOException {
        return seriesResponse(seriesId, response, false);
    }

    static JSONObject seriesResponse(String seriesId, JSONObject response, boolean direct) throws IOException {
        if (!zero(response.opt("code")) || response.has("error_code") && !zero(response.opt("error_code")))
            throw new IOException("红果 App 目录暂未返回该剧");
        JSONObject data = response.optJSONObject("data");
        JSONObject item = direct || data == null ? data : data.optJSONObject(seriesId);
        JSONObject series = item == null ? null : item.optJSONObject("video_data");
        if (series != null && !seriesId.equals(series.optString("series_id")))
            throw new IOException("红果 App 分集目录校验未通过");
        JSONArray rows = series == null ? null : series.optJSONArray("video_list");
        if (rows == null || rows.length() == 0) throw new MissingDirectory("红果 App 暂未返回该剧分集");
        return series;
    }

    private static boolean zero(Object value) {
        return "0".equals(value) || value instanceof Number && ((Number) value).doubleValue() == 0;
    }

    static boolean addsEpisodes(JSONObject existing, JSONObject candidate) throws Exception {
        if (!existing.getString("seriesId").equals(candidate.getString("seriesId"))) return false;
        JSONArray before = existing.getJSONArray("episodes"), after = candidate.getJSONArray("episodes");
        if (after.length() <= before.length() || candidate.optInt("episodeCount") < existing.optInt("episodeCount")) return false;
        Map<Integer, String> ids = new HashMap<>();
        for (int i = 0; i < after.length(); i++) {
            JSONObject row = after.getJSONObject(i);
            ids.put(row.getInt("index"), row.getString("vid"));
        }
        for (int i = 0; i < before.length(); i++) {
            JSONObject row = before.getJSONObject(i);
            if (!row.getString("vid").equals(ids.get(row.getInt("index")))) return false;
        }
        return true;
    }

    static JSONObject project(String seriesId, JSONObject series) throws Exception {
        JSONArray list = series.getJSONArray("video_list"), episodes = new JSONArray();
        if (list.length() > 1000) throw new IOException("红果 App 分集目录超过上限");
        TreeMap<Integer, JSONObject> ordered = new TreeMap<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject row = list.optJSONObject(i);
            if (row == null || row.has("series_id") && !row.isNull("series_id") &&
                    !seriesId.equals(row.optString("series_id")))
                throw new IOException("红果 App 分集身份不匹配");
            String vid = row.optString("vid"), rawIndex = row.optString("vid_index");
            if (!OfficialApiClient.validID(vid) || !rawIndex.matches("[1-9][0-9]{0,3}|10000"))
                throw new IOException("红果 App 分集编号或集号无效");
            int index = Integer.parseInt(rawIndex);
            if (ordered.containsKey(index) || !ids.add(vid)) throw new IOException("红果 App 分集编号或集号重复");
            ordered.put(index, new JSONObject().put("index", index)
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
