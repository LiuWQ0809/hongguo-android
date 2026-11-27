package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class CalendarPage {
    static JSONObject project(JSONObject reply, String requestedDay, int offset, int previousSubscribe) throws Exception {
        JSONObject data = reply.optJSONObject("data");
        if (reply.optInt("code", -1) != 0 || data == null) throw new IOException("红果上新日历暂不可用");
        JSONObject calendar = data.optJSONObject("calendar_schema");
        if (calendar == null) throw new IOException("红果未返回日历日期");
        String selected = calendar.optString("default_date"), today = calendar.optString("current_date");
        JSONArray dateList = calendar.optJSONArray("date_list");
        Set<String> dates = new LinkedHashSet<>();
        for (int i = 0; dateList != null && i < Math.min(dateList.length(), 40); i++) {
            String date = dateList.optString(i);
            if (date.matches("20[0-9]{6}")) dates.add(date);
        }
        if (!dates.contains(selected) || !dates.contains(today) || !requestedDay.isEmpty() && !selected.equals(requestedDay))
            throw new IOException("日历返回日期与请求不一致");
        Set<String> seen = new LinkedHashSet<>();
        JSONArray rows = data.optJSONArray("subscribe_items");
        JSONArray items = new JSONArray();
        for (int i = 0; rows != null && i < Math.min(rows.length(), 100); i++) {
            JSONObject item = CalendarEntry.project(rows.optJSONObject(i), selected);
            if (item != null && seen.add(item.getString("seriesId"))) items.put(item);
        }
        JSONObject location = data.optJSONObject("calendar_loc_info");
        int next = Math.max(offset, data.optInt("next_offset"));
        int subscribe = location == null ? previousSubscribe : location.optInt("subscribe_offset", previousSubscribe);
        boolean more = data.optBoolean("has_more") || location != null && location.optBoolean("down_has_more");
        return new JSONObject().put("selected", selected).put("today", today).put("dates", new JSONArray(dates))
            .put("items", items).put("hasMore", more && (next > offset || subscribe > previousSubscribe))
            .put("offset", next).put("subscribeOffset", subscribe).put("sessionId", data.optString("session_id"));
    }
}
