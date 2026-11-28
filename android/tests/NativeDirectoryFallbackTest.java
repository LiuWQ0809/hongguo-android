package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class NativeDirectoryFallbackTest {
    private static final String SID = "123456789";
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static JSONObject pack(String seriesId, int index, String vid) throws Exception {
        JSONObject first = new JSONObject().put("vid", "123456781").put("vid_index", 1);
        JSONArray rows = new JSONArray().put(first);
        if (index != 1) rows.put(new JSONObject().put("vid", vid).put("vid_index", index));
        return new JSONObject().put("series_id", seriesId).put("episode_cnt", 8).put("video_list", rows);
    }
    private static JSONObject candidate(String id) throws Exception {
        return new JSONObject().put("seriesId", id);
    }
    private static void rejectQueue(JSONArray rows, String selected) throws Exception {
        try {
            new RecommendationQueue(rows, selected);
            throw new AssertionError("invalid recommendation queue");
        } catch (JSONException expected) { }
    }
    private static void recommendations(JSONArray episodes) throws Exception {
        String previous = "223456789", next = "323456789";
        JSONArray rows = new JSONArray().put(candidate(previous)).put(candidate(SID)).put(candidate(next));
        RecommendationQueue queue = new RecommendationQueue(rows, SID);
        require(previous.equals(queue.neighbor(-1)) && next.equals(queue.neighbor(1)));
        require(queue.neighbor(0) == null);
        RecommendationQueue head = new RecommendationQueue(queue.rows(), previous);
        require(head.neighbor(-1) == null && SID.equals(head.neighbor(1)));
        rejectQueue(rows, "423456789");
        rejectQueue(new JSONArray().put(candidate(SID)).put(candidate("123")), SID);
        rejectQueue(new JSONArray().put(candidate(SID)).put(candidate(SID)), SID);
        rejectQueue(new JSONArray().put(candidate(SID)).put(candidate("9912345678")), SID);
        rejectQueue(new JSONArray().put(candidate(SID)).put(new JSONObject().put("seriesId", 123456829)), SID);
        require(RecommendationQueue.startEpisode(episodes, null).getInt("index") == 1);
        JSONObject fourth = episodes.getJSONObject(1);
        require(RecommendationQueue.startEpisode(episodes, fourth).getInt("index") == 4);
        require(RecommendationQueue.startEpisode(episodes, new JSONObject(fourth.toString()).put("index", 5)).getInt("index") == 1);
        try {
            RecommendationQueue.startEpisode(new JSONArray().put(fourth), null);
            throw new AssertionError("partial directory must not start at episode 4");
        } catch (JSONException expected) { }
    }
    public static void main(String[] args) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JSONObject partial = NativeSeriesDetail.project(SID, pack(SID, 1, ""));
        JSONObject extension = NativeSeriesDetail.project(SID, pack(SID, 4, "123456784"));
        JSONObject changed = pack(SID, 4, "123456784");
        changed.getJSONArray("video_list").getJSONObject(0).put("vid", "123456782");
        JSONObject conflict = NativeSeriesDetail.project(SID, changed);
        require(NativeDirectoryFallback.read(deadline, first -> { require(first < deadline); return partial; },
            second -> { require(second == deadline); return extension; }) == extension);
        require(NativeDirectoryFallback.read(deadline, first -> partial, second -> conflict) == partial);
        require(NativeDirectoryFallback.read(deadline, first -> { throw new SocketTimeoutException(); }, second -> extension) == extension);
        AtomicInteger extra = new AtomicInteger();
        try {
            NativeDirectoryFallback.read(deadline, first -> NativeSeriesDetail.seriesResponse(SID, new JSONObject().put("code", 403)),
                second -> { extra.incrementAndGet(); return extension; });
            throw new AssertionError("business refusal must remain terminal");
        } catch (IOException expected) { require(extra.get() == 0); }
        try {
            NativeDirectoryFallback.read(deadline, first -> partial, second -> { throw new InterruptedIOException("fixture"); });
            throw new AssertionError("cancellation must stay terminal even with partial data");
        } catch (InterruptedIOException expected) { require("fixture".equals(expected.getMessage())); }
        JSONArray episodes = extension.getJSONArray("episodes");
        require(episodes.getJSONObject(1).getInt("index") == 4);
        require(!episodes.getJSONObject(1).getBoolean("webAvailable"));
        recommendations(episodes);
        JSONObject foreign = new JSONObject().put("code", 0).put("data", new JSONObject().put("video_data", pack("223456789", 4, "123456784")));
        try { NativeSeriesDetail.seriesResponse(SID, foreign, true); throw new AssertionError("foreign identity"); }
        catch (IOException expected) { require(!(expected instanceof NativeSeriesDetail.MissingDirectory)); }
        System.out.println("NATIVE_DIRECTORY_FALLBACK_OK");
    }
}
