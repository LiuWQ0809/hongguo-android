package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class ReleaseCalendar {
    private static JSONObject session;

    static synchronized JSONObject fetch(JSONObject args) throws Exception {
        String date = args.optString("date");
        if (!date.isEmpty()) {
            if (!date.matches("20[0-9]{6}")) throw new IOException("上新日历日期无效");
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        }
        String sessionId = args.optString("sessionId");
        int offset = args.optInt("offset", 0), subscribe = args.optInt("subscribeOffset", -1);
        if (sessionId.length() > 256 || offset < 0 || offset > 10000 || subscribe < -1 || subscribe > 10000)
            throw new IOException("上新日历分页无效");
        JSONObject query = new JSONObject().put("offset", offset).put("session_id", sessionId)
            .put("tab_type", 5).put("tab_style", 2).put("active_panel", 6).put("gender_type", 2)
            .put("need_calendar_schema", "true");
        if (!date.isEmpty()) query.put("target_date", date);
        if (subscribe >= 0) query.put("subscribe_offset", subscribe);
        try (OfficialApiClient client = new OfficialApiClient(session == null ? OfficialApiClient.newSession() : session)) {
            client.deadlineNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(18));
            client.registerDevice();
            session = client.session();
            JSONObject response = client.cloud("/reading/user/subscribe/list/v1/", "GET", query, null, false);
            return CalendarPage.project(response, date, offset, subscribe);
        }
    }
}
