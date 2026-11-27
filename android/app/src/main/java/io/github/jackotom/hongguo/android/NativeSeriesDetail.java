package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
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
            return seriesResponse(seriesId, response);
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
}
