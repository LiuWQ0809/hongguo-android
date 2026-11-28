package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class BrowseQuery {
    static final List<String> FILTERS = Arrays.asList("genre", "category_dim_theme", "category_dim_role",
        "category_dim_epoch", "sort", "gender", "online_time", "creation_status");

    private BrowseQuery() { }

    static boolean optionId(Object value) {
        return value instanceof String && ((String) value).matches("[a-z0-9_]{1,40}");
    }

    static JSONObject body(JSONObject choices, int offset, String session, boolean selectors) throws Exception {
        if (offset < 0 || offset > 10000 || session.length() > 256 || offset > 0 && session.isEmpty())
            throw new IOException("找剧分页无效");
        for (Iterator<String> keys = choices.keys(); keys.hasNext();)
            if (!FILTERS.contains(keys.next())) throw new IOException("找剧筛选项无效");
        JSONObject selection = new JSONObject();
        for (String key : FILTERS) {
            Object value = choices.opt(key);
            if (value != null && !optionId(value)) throw new IOException("找剧筛选值无效");
            JSONArray values = new JSONArray();
            if (value != null && !"all".equals(value)) values.put(value);
            selection.put(key, values);
        }
        String genre = choices.optString("genre");
        String scene = "ai_series".equals(genre) ? "ai_series" : "default";
        return new JSONObject().put("filter_ids", "").put("req_scene", scene)
            .put("offset", offset).put("need_selector_panel", selectors).put("limit", 24)
            .put("select_items", selection).put("session_id", session)
            .put("req_type", selectors ? "default" : "only_content")
            .put("client_req_type", selectors ? 1 : 3);
    }
}
