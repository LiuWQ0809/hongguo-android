package io.github.jackotom.hongguo.android;

import org.json.JSONArray;
import org.json.JSONObject;

/** Production validation rules with no network, decoder or account access. */
public final class SeasonContinuationTest {
    private static final String CURRENT = "1234567890123456789";
    private static final String NEXT = "1234567890123456790";
    private static final String DUPLICATE = "1234567890123456791";

    private static JSONObject row(String id, String title, int number, int episodes, boolean playable) throws Exception {
        return new JSONObject().put("seriesId", id).put("title", title).put("seasonNumber", number)
            .put("episodeCount", episodes).put("playable", playable).put("upcoming", !playable);
    }

    private static JSONObject official(JSONObject... rows) throws Exception {
        JSONArray items = new JSONArray();
        for (JSONObject item : rows) items.put(item);
        return new JSONObject().put("matching", "official").put("complete", true).put("items", items);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        JSONObject current = row(CURRENT, "某剧第六季", 6, 220, true);
        JSONObject next = row(NEXT, "某剧第七季", 7, 210, true);
        JSONObject complete = official(next, current);
        check(NEXT.equals(SeasonContinuation.next(complete, CURRENT, 220).getString("seriesId")), "adjacent official season");
        check(SeasonContinuation.next(complete, CURRENT, 219) == null, "early ending must stay in current season");
        check(SeasonContinuation.next(complete, DUPLICATE, 220) == null, "must confirm current series ID");
        check(SeasonContinuation.next(complete.put("complete", false), CURRENT, 220) == null, "truncated relations stay manual");
        check(SeasonContinuation.next(complete.put("complete", true).put("matching", "title"), CURRENT, 220) == null, "search is insufficient");
        check(SeasonContinuation.next(official(current, next, row(DUPLICATE,"同季异版",7,210,true)), CURRENT, 220) == null, "ambiguous season");
        check(SeasonContinuation.next(official(current, row(NEXT,"待上线",7,0,false)), CURRENT, 220) == null, "reservation cannot play");
        check(SeasonContinuation.next(official(current, row(NEXT,"下一季缺失",8,210,true)), CURRENT, 220) == null, "no skipped seasons");
        check(NEXT.equals(SeasonContinuation.next(official(row(CURRENT,"某剧第六季",0,220,true),
            row(NEXT,"某剧第七季",0,210,true)), CURRENT,220).getString("seriesId")), "explicit Chinese titles in native relation");
        check(!SeasonContinuation.atFinale(current, new JSONObject().put("index",219)), "local episode count guard");
        check(SeasonContinuation.atFinale(current, new JSONObject().put("index",220)), "verified ending");
        PlaybackCuts cuts = new PlaybackCuts(10, 15);
        check(cuts.start(0,100_000)==10_000 && cuts.start(3_000,100_000)==3_000, "intro preserves explicit resume");
        check(cuts.start(0,15_000)==0 && !cuts.finishesAt(12_000,15_000), "do not skip short clips");
        check(!cuts.finishesAt(84_999,100_000) && cuts.finishesAt(85_000,100_000), "outro boundary");
        check(!PlaybackCuts.DEFAULT.finishesAt(100_000,100_000), "skip disabled by default");
        System.out.println("SEASON_CONTINUATION_AND_CUTS_OK");
    }
}
