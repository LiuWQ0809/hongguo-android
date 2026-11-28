package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class OfficialBrowse {
    private static JSONObject session;

    static synchronized JSONObject fetch(JSONObject args) throws Exception {
        int offset = args.optInt("offset", 0);
        String cursor = args.optString("sessionId");
        JSONObject choices = args.optJSONObject("filters");
        if (choices == null) choices = new JSONObject();
        JSONObject body = BrowseQuery.body(choices, offset, cursor, offset == 0);
        try (OfficialApiClient client = new OfficialApiClient(session == null ? OfficialApiClient.newSession() : session)) {
            client.deadlineNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(17));
            client.registerDevice();
            session = client.session();
            JSONObject reply = client.cloud(NewReleaseCatalog.CATEGORY_API, "POST", null, body, false);
            if (reply.optInt("code", -1) != 0) throw new IOException("红果找剧暂未返回结果");
            JSONObject data = reply.optJSONObject("data");
            JSONArray rows = data == null ? null : data.optJSONArray("video_data");
            if (rows == null) throw new IOException("红果找剧结果格式无效");
            Set<String> seen = new HashSet<>();
            JSONArray items = new JSONArray();
            for (int i = 0; i < Math.min(60, rows.length()); i++) {
                JSONObject item = NewReleaseCatalog.row(rows.optJSONObject(i));
                if (item != null && seen.add(item.getString("series_id"))) items.put(item);
            }
            int next = data.optInt("next_offset", offset);
            String nextSession = data.optString("session_id", cursor);
            boolean more = data.optBoolean("has_more") && next > offset && next <= 10000 && !nextSession.isEmpty();
            return new JSONObject().put("items", items).put("offset", next).put("sessionId", nextSession)
                .put("hasMore", more).put("groups", BrowseChoices.project(data.optJSONArray("selector_rows")));
        }
    }
}
