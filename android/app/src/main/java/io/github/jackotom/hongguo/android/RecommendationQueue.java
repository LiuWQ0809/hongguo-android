package io.github.jackotom.hongguo.android;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RecommendationQueue {
    static final String EXTRA = "recommendations";
    private final List<String> ids = new ArrayList<>();
    private final int current;

    RecommendationQueue(Object value, String seriesId) throws JSONException {
        if (!(value instanceof JSONArray)) throw new JSONException("推荐播放清单无效");
        JSONArray rows = (JSONArray) value;
        if (rows.length() < 2 || rows.length() > 24) throw new JSONException("推荐播放清单数量无效");
        for (int i = 0; i < rows.length(); i++) {
            Object raw = rows.getJSONObject(i).opt("seriesId");
            if (!(raw instanceof String) || ((String) raw).length() < 6 || !OfficialApiClient.validID((String) raw) || ids.contains(raw)
                    || PlaybackSources.isRetiredSeries((String) raw)) throw new JSONException("推荐播放清单包含无效或重复剧集");
            ids.add((String) raw);
        }
        current = ids.indexOf(seriesId);
        if (current < 0) throw new JSONException("当前短剧不在推荐播放清单中");
    }

    String neighbor(int direction) {
        int next = current + Integer.signum(direction);
        return direction == 0 || next < 0 || next >= ids.size() ? null : ids.get(next);
    }

    JSONArray rows() throws JSONException {
        JSONArray rows = new JSONArray();
        for (String id : ids) rows.put(new JSONObject().put("seriesId", id));
        return rows;
    }

    static JSONObject startEpisode(JSONArray episodes, JSONObject history) throws JSONException {
        if (history != null) for (int i = 0; i < episodes.length(); i++) {
            JSONObject episode = episodes.getJSONObject(i);
            if (episode.optString("vid").equals(history.optString("vid"))
                    && episode.optInt("index") == history.optInt("index")) return episode;
        }
        JSONObject first = episodes.getJSONObject(0);
        if (first.getInt("index") != 1) throw new JSONException("推荐短剧首集缺失");
        return first;
    }
}
