package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.Intent;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Advance only from the confirmed ending to the adjacent, available official season. */
final class SeasonContinuation {
    final JSONObject series;
    final JSONArray episodes;

    private SeasonContinuation(JSONObject series, JSONArray episodes) {
        this.series = series;
        this.episodes = episodes;
    }

    static boolean atFinale(JSONObject series, JSONObject episode) {
        return series != null && episode != null && episode.optInt("index") > 0
            && episode.optInt("index") == series.optInt("episodeCount");
    }

    static JSONObject next(JSONObject relationship, String sid, int finale) throws Exception {
        if (!"official".equals(relationship.optString("matching")) || !relationship.optBoolean("complete")) return null;
        JSONArray seasons = relationship.getJSONArray("items");
        int current = 0;
        for (int i = 0; i < seasons.length(); i++) {
            JSONObject row = seasons.getJSONObject(i);
            if (sid.equals(row.optString("seriesId")) && row.optInt("episodeCount") == finale)
                current = seasonNumber(row);
        }
        if (current <= 0) return null;
        JSONObject next = null;
        for (int i = 0; i < seasons.length(); i++) {
            JSONObject row = seasons.getJSONObject(i);
            if (seasonNumber(row) != current + 1) continue;
            if (next != null || sid.equals(row.optString("seriesId"))) return null;
            next = row;
        }
        return next != null && next.optBoolean("playable") && !next.optBoolean("upcoming") ? next : null;
    }

    private static int seasonNumber(JSONObject row) {
        int explicit = row.optInt("seasonNumber");
        if (explicit > 0) return explicit;
        var suffix = java.util.regex.Pattern.compile("第([1-9][0-9]{0,2}|[一二三四五六七八九]|十[一二三四五六七八九]?)季$").matcher(row.optString("title"));
        if (!suffix.find()) return 0;
        String number = suffix.group(1);
        if (number.matches("[0-9]+")) return Integer.parseInt(number);
        String units = "〇一二三四五六七八九";
        if (number.startsWith("十")) return 10 + (number.length() == 1 ? 0 : units.indexOf(number.charAt(1)));
        return units.indexOf(number.charAt(0));
    }

    static SeasonContinuation load(Context context, JSONObject owner, JSONObject episode) throws Exception {
        if (!atFinale(owner, episode)) return null;
        String sid = owner.getString("seriesId");
        JSONObject next = next(new OfficialSeriesService(context).load(sid, client -> { }), sid, episode.getInt("index"));
        if (next == null || Thread.currentThread().isInterrupted()) return null;
        String nextSid = next.getString("seriesId");
        JSONObject detail = NativeSeriesDetail.fetch(nextSid);
        JSONArray rows = detail.getJSONArray("episodes");
        if (!nextSid.equals(detail.getString("seriesId")) || rows.length() == 0
            || rows.length() != detail.getInt("episodeCount") || rows.length() != next.optInt("episodeCount"))
            throw new IOException("下一季原生分集目录尚未完整");
        for (int i = 0; i < rows.length(); i++)
            if (rows.getJSONObject(i).getInt("index") != i + 1) throw new IOException("下一季分集编号不连续");
        return new SeasonContinuation(LibraryStore.series(detail), rows);
    }

    Intent intent(Context context, String quality, float speed, boolean cleanup) throws Exception {
        JSONObject options = new JSONObject().put("quality", quality).put("speed", speed)
            .put("autoplay", true).put("cleanupPlaybackCache", cleanup).put("resolveInPlayer", true);
        return PlayLauncher.build(context, series, episodes.getJSONObject(0), episodes, null, false, options);
    }
}
