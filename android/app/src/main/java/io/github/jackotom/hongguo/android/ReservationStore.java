package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationStore {
    private static final int LIMIT = 40;
    private static final int MESSAGE_LIMIT = 60;
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

    JSONArray messages() throws Exception {
        synchronized (ReservationStore.class) {
            return new JSONArray(prefs.getString("messages", "[]"));
        }
    }

    void readMessage(String id) throws Exception {
        if (!id.matches("[0-9a-f-]{36}")) throw new IOException("消息编号无效");
        synchronized (ReservationStore.class) {
            JSONArray rows = messages();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!id.equals(row.getString("messageId")) || row.optBoolean("read")) continue;
                row.put("read", true);
                saveMessageChange(null, rows, "消息状态保存失败");
                return;
            }
        }
    }

    private void saveChange(JSONArray rows, JSONObject row) throws Exception {
        JSONObject message = new JSONObject().put("messageId", UUID.randomUUID().toString())
            .put("seriesId", row.getString("seriesId")).put("title", row.getString("title"))
            .put("follow", row.optBoolean("follow")).put("latestEpisode", row.optInt("latestEpisode"))
            .put("arrivedAt", System.currentTimeMillis()).put("read", false);
        JSONArray previous = messages(), next = new JSONArray().put(message);
        for (int i = 0; i < previous.length() && next.length() < MESSAGE_LIMIT; i++)
            next.put(previous.getJSONObject(i));
        saveMessageChange(rows, next, "上线或追更消息保存失败");
    }

    private void saveMessageChange(JSONArray items, JSONArray messages, String error) throws IOException {
        String originalMessages = prefs.getString("messages", null), originalItems = prefs.getString("items", null);
        SharedPreferences.Editor editor = prefs.edit().putString("messages", messages.toString());
        if (items != null) editor.putString("items", items.toString());
        if (!editor.commit()) {
            SharedPreferences.Editor restore = prefs.edit().putString("messages", originalMessages);
            if (items != null) restore.putString("items", originalItems);
            restore.commit();
            throw new IOException(error);
        }
    }

    void update(String id, JSONObject series, boolean enabled, boolean follow, int latestEpisode) throws Exception {
        if (!OfficialApiClient.validID(id)) throw new IOException("剧集编号无效");
        synchronized (ReservationStore.class) {
            JSONArray rows = read();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!id.equals(row.getString("seriesId")) || follow != row.optBoolean("follow")) continue;
                if (!enabled) { rows.remove(i); save(rows); }
                return;
            }
            if (!enabled) return;
            if (rows.length() >= LIMIT) throw new IOException("本机预约和更新提醒已满，请先取消一项");
            JSONObject row = LibraryStore.series(series);
            if (!id.equals(row.getString("seriesId")) || row.optString("title").isEmpty())
                throw new IOException("预约剧集资料不完整");
            row.put("expected", LibraryStore.text(series, "expected", 80))
                .put("token", UUID.randomUUID().toString()).put("online", follow);
            if (follow) row.put("follow", true).put("latestEpisode", latestEpisode)
                .put("notified", true).put("checkedAt", System.currentTimeMillis());
            save(rows.put(row));
        }
    }

    void checked(JSONObject expected, boolean online, int latestEpisode) throws Exception {
        synchronized (ReservationStore.class) {
            JSONArray rows = read();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!expected.getString("token").equals(row.getString("token"))) continue;
                boolean changed;
                if (row.optBoolean("follow")) {
                    changed = latestEpisode > row.optInt("latestEpisode");
                    if (changed) row.put("latestEpisode", latestEpisode).put("notified", false);
                } else if (row.optBoolean("online")) return;
                else { changed = online; row.put("online", online); }
                row.put("checkedAt", System.currentTimeMillis());
                if (changed) saveChange(rows, row); else save(rows);
                return;
            }
        }
    }

    void notifyReady(java.util.function.Predicate<JSONObject> notify) throws Exception {
        synchronized (ReservationStore.class) {
            JSONArray rows = read();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row.optBoolean("online") && !row.optBoolean("notified") && notify.test(row)) {
                    row.put("notified", true);
                    save(rows);
                }
            }
        }
    }
}
