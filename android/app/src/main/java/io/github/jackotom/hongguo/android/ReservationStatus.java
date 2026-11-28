package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationStatus {
    private ReservationStatus() { }

    static boolean published(String seriesId, long deadline) throws Exception {
        if (!OfficialApiClient.validID(seriesId)) throw new IOException("预约剧集编号无效");
        NetworkGuard.Reply reply = NetworkGuard.get("https://hongguoduanju.com/detail?series_id=" + seriesId,
            Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(5)));
        if (reply.status == 404) return false;
        if (reply.status != 200) throw new IOException("上线查询暂不可用");
        if (!published(seriesId, EpisodeResolver.extractRouterData(reply.body))) return false;
        JSONObject nativeDetail = NativeSeriesDetail.fetch(seriesId, deadline);
        JSONArray episodes = nativeDetail.optJSONArray("episodes");
        JSONObject first = episodes == null ? null : episodes.optJSONObject(0);
        return first != null && OfficialApiClient.validID(first.optString("vid"));
    }

    static boolean published(String seriesId, JSONObject router) {
        if (!OfficialApiClient.validID(seriesId)) return false;
        JSONObject loader = router.optJSONObject("loaderData");
        JSONObject page = loader == null ? null : loader.optJSONObject("detail_page");
        if (page == null || !Boolean.TRUE.equals(page.opt("isSuccess"))) return false;
        JSONObject series = page.optJSONObject("seriesDetail");
        if (series == null || !seriesId.equals(series.opt("series_id")) || series.optInt("episode_cnt", 0) < 1)
            return false;
        JSONArray vids = series.optJSONArray("vid_list");
        String first = vids == null ? "" : vids.optString(0);
        return OfficialApiClient.validID(first) || first.matches("v1_[A-Za-z0-9_-]{16,120}");
    }
}
