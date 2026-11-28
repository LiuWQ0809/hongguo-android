package io.github.jackotom.hongguo.android;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class BrowseChoices {
    private BrowseChoices() { }

    static JSONArray project(JSONArray source) throws Exception {
        JSONArray groups = new JSONArray();
        Set<String> supported = new HashSet<>();
        for (int i = 0; source != null && i < Math.min(source.length(), 30); i++) {
            JSONObject row = source.optJSONObject(i);
            if (row == null) continue;
            String key = row.optString("type"), name = LibraryStore.text(row, "row_name", 24);
            if (!BrowseQuery.FILTERS.contains(key) || !supported.add(key)) continue;
            JSONArray items = row.optJSONArray("items"), values = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (int j = 0; items != null && j < Math.min(items.length(), 150); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                Object id = item.opt("selector_item_id");
                String label = LibraryStore.text(item, "show_name", 32);
                if (BrowseQuery.optionId(id) && !label.isEmpty() && seen.add((String) id))
                    values.put(new JSONObject().put("id", id).put("label", label));
            }
            if (values.length() > 0) groups.put(new JSONObject().put("key", key).put("label", name).put("items", values));
        }
        return groups;
    }
}
