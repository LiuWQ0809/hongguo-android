package io.github.jackotom.hongguo.android;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class ActorRows {
    static JSONArray project(JSONArray cells, int offset) throws Exception {
        JSONArray rows = new JSONArray();
        Set<String> seen = new HashSet<>();
        int rank = offset;
        for (int i = 0; i < Math.min(40, cells.length()); i++) {
            JSONObject cell = cells.optJSONObject(i);
            JSONArray actors = cell == null ? null : cell.optJSONArray("ugc_user_data");
            for (int j = 0; actors != null && j < Math.min(20, actors.length()); j++) {
                rank++;
                JSONObject item = actors.optJSONObject(j);
                JSONObject profile = item == null ? null : item.optJSONObject("user_info");
                JSONObject base = profile == null ? null : profile.optJSONObject("base_info");
                if (base == null || base.optBoolean("is_ai_bot") || base.optBoolean("is_cancelled")) continue;
                String id = profile.optString("user_id", base.optString("user_id"));
                String name = LibraryStore.text(base, "user_name", 80);
                if (!OfficialApiClient.validID(id) || name.isEmpty() || !seen.add(id)) continue;
                JSONObject stats = profile.optJSONObject("user_stat");
                int works = stats == null ? 0 : stats.optInt("celebrity_work_cnt");
                rows.put(new JSONObject().put("id", id).put("name", name).put("rank", rank)
                    .put("avatar", EpisodeResolver.publicHttpsUrl(base.optString("user_avatar")))
                    .put("works", Math.max(0, Math.min(10000, works))));
                if (rows.length() >= 40) return rows;
            }
        }
        return rows;
    }
}
