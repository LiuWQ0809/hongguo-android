package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class ActorDirectory {
    private static JSONObject session;

    static synchronized JSONObject fetch(JSONObject args) throws Exception {
        int offset = args.optInt("offset");
        String cursor = args.optString("sessionId");
        if (offset < 0 || offset > 10000 || cursor.length() > 256 || offset > 0 && cursor.isEmpty())
            throw new IOException("演员目录分页无效");
        JSONObject query = request(offset, cursor);
        try (OfficialApiClient client = new OfficialApiClient(session == null ? OfficialApiClient.newSession() : session)) {
            client.deadlineNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(16));
            client.registerDevice();
            session = client.session();
            JSONObject reply = client.cloud("/reading/bookapi/bookmall/cell/change/v", "GET", query, null, false);
            JSONObject data = reply.optInt("code", -1) == 0 ? reply.optJSONObject("data") : null;
            JSONObject view = data == null ? null : data.optJSONObject("cell_view");
            JSONArray cells = view == null ? null : view.optJSONArray("cell_data");
            if (cells == null) throw new IOException("演员目录暂未返回");
            JSONArray actors = ActorRows.project(cells, offset);
            int next = data.optInt("next_offset", offset);
            String nextCursor = data.optString("session_id", cursor);
            boolean more = actors.length() > 0 && data.optBoolean("has_more") && next > offset && next <= 10000 &&
                !nextCursor.isEmpty() && nextCursor.length() <= 256;
            return new JSONObject().put("items", actors).put("offset", next).put("sessionId", nextCursor).put("hasMore", more);
        }
    }

    private static JSONObject request(int offset, String cursor) throws Exception {
        return new JSONObject().put("cell_id", "7470092475068071998").put("cell_sub_id", "0")
            .put("selected_items", "ranklist_celebrity").put("sub_selected_items", "ranklist_series_actor")
            .put("tab_type", offset == 0 ? "26" : "18").put("client_req_type", "2").put("client_template", "2")
            .put("category_id", "0").put("gender", "2").put("limit", "20").put("offset", offset).put("session_id", cursor)
            .put("background_selected_items", "").put("panel_selected_items", "").put("filter_ids", "")
            .put("unlimited_selector_change_type", "0");
    }
}
