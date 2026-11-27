package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationStore {
    private static final int LIMIT = 40;
    private final SharedPreferences prefs;

    ReservationStore(Context context) {
        prefs = context.getSharedPreferences("reservation_library", Context.MODE_PRIVATE);
    }

    private JSONArray read() throws Exception {
        return new JSONArray(prefs.getString("items", "[]"));
    }

    private void save(JSONArray rows) throws IOException {
        if (!prefs.edit().putString("items", rows.toString()).commit()) throw new IOException("预约保存失败");
    }

    JSONArray snapshot() throws Exception {
        synchronized (ReservationStore.class) { return read(); }
    }

    void update(String id, JSONObject series, boolean enabled) throws Exception {
        if (!OfficialApiClient.validID(id)) throw new IOException("剧集编号无效");
        synchronized (ReservationStore.class) {
            JSONArray rows = read();
            for (int i = 0; i < rows.length(); i++) {
                if (!id.equals(rows.getJSONObject(i).getString("seriesId"))) continue;
                if (!enabled) { rows.remove(i); save(rows); }
                return;
            }
            if (!enabled) return;
            if (rows.length() >= LIMIT) throw new IOException("本机预约已满，请先取消一项");
            JSONObject row = LibraryStore.series(series);
            if (!id.equals(row.getString("seriesId")) || row.optString("title").isEmpty())
                throw new IOException("预约剧集资料不完整");
            save(rows.put(row.put("expected", LibraryStore.text(series, "expected", 80))
                .put("token", UUID.randomUUID().toString()).put("online", false)));
        }
    }
}
