package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
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
        JSONObject foreign = new JSONObject().put("code", 0).put("data", new JSONObject().put("video_data", pack("223456789", 4, "123456784")));
        try { NativeSeriesDetail.seriesResponse(SID, foreign, true); throw new AssertionError("foreign identity"); }
        catch (IOException expected) { require(!(expected instanceof NativeSeriesDetail.MissingDirectory)); }
        System.out.println("NATIVE_DIRECTORY_FALLBACK_OK");
    }
}
