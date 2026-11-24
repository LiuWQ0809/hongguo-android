package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Copies the independent app's local snapshot without touching its source or account storage. */
final class LegacyLibraryImport {
    private LegacyLibraryImport() {}

    static void migrate(Context context, SharedPreferences destination) {
        if (destination.contains("local19_imported")) return;
        try {
            String snapshot = context.getSharedPreferences("hongguo_library", Context.MODE_PRIVATE).getString("snapshot", null);
            if (snapshot == null || snapshot.length() > 2_000_000) return;
            JSONObject root = new JSONObject(snapshot);
            if (root.optInt("version") != 1) return;
            SharedPreferences.Editor editor = destination.edit();
            for (String key : new String[]{"history", "favorites"}) {
                if (!destination.contains(key)) editor.putString(key, rows(root.getJSONArray(key), key.equals("history")).toString());
            }
            editor.putBoolean("local19_imported", true).commit();
        } catch (JSONException | ClassCastException invalid) {
            // A damaged or future snapshot remains available to recover separately.
        }
    }

    private static JSONArray rows(JSONArray old, boolean history) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < old.length() && result.length() < (history ? 100 : 200); i++) {
            JSONObject entry = old.getJSONObject(i), card = entry.getJSONObject("card");
            String id = card.getString("id"), vid = entry.optString("episode_id");
            String title = card.getString("title").trim();
            if (title.isEmpty() || !id.matches("[0-9]{6,30}") || (history && !vid.matches("[0-9]{6,30}"))) continue;
            if (title.length() > 200) title = title.substring(0, 200);
            JSONObject row = LibraryStore.series(new JSONObject().put("seriesId", id).put("title", title)
                .put("coverUrl", card.optString("cover")).put("episodeCount", card.optInt("episode_count"))
                .put("description", card.optString("summary")));
            if (history) row.put("vid", vid).put("index", Math.max(1, entry.optInt("episode_index")))
                .put("positionMs", Math.max(0, entry.optLong("position_ms")));
            result.put(row.put(history ? "updatedAt" : "favoriteAt", Math.max(1, entry.optLong("updated_at"))));
        }
        return result;
    }
}
