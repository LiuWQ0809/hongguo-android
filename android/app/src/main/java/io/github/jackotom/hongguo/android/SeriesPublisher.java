package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class SeriesPublisher {
    private SeriesPublisher() { }

    static JSONObject fetch(JSONArray ids) throws Exception {
        if (ids.length() < 1 || ids.length() > 20) throw new IOException("发布账号查询数量无效");
        Set<String> requested = new LinkedHashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object id = ids.get(i);
            if (!(id instanceof String) || !OfficialApiClient.validID((String) id))
                throw new IOException("发布账号查询的剧集编号无效");
            requested.add((String) id);
        }
        JSONObject query = new JSONObject().put("book_id", String.join(",", requested)).put("book_type", 2);
        try (OfficialApiClient client = new OfficialApiClient(OfficialApiClient.newSession())) {
            client.deadlineNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(16));
            JSONObject result = client.cloud("/reading/bookapi/multi-detail/v1/", "GET", query, null, false);
            return project(requested, result);
        }
    }

    static JSONObject project(Set<String> requested, JSONObject result) throws Exception {
        JSONArray rows = result.optJSONArray("data");
        if (result.optInt("code", -1) != 0 || rows == null || rows.length() > 100)
            throw new IOException("发布账号资料暂未返回");
        JSONObject accounts = new JSONObject();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("book_id"), account = row.optString("media_id");
            String name = row.optString("source").trim();
            if (name.isEmpty()) name = row.optString("publisher").trim();
            if (requested.contains(id) && OfficialApiClient.validID(account) && !name.isEmpty() && name.length() <= 100)
                accounts.put(id, new JSONObject().put("id", account).put("name", name));
        }
        return accounts;
    }
}
