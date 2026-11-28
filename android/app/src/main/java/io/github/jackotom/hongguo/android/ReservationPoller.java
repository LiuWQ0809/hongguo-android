package io.github.jackotom.hongguo.android;

import android.content.Context;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationPoller {
    private static final ReentrantLock RUNNING = new ReentrantLock();
    private static final int BATCH = 8;

    static boolean check(Context context) throws Exception {
        if (!RUNNING.tryLock()) return false;
        try {
            ReservationStore store = new ReservationStore(context);
            JSONArray rows = store.snapshot();
            ArrayList<JSONObject> pending = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row.optBoolean("follow") || !row.optBoolean("online")) pending.add(row);
            }
            pending.sort(Comparator.comparingLong(row -> row.optLong("checkedAt")));
            boolean complete = true;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(39);
            for (JSONObject row : pending.subList(0, Math.min(BATCH, pending.size()))) {
                if (Thread.currentThread().isInterrupted()) return false;
                if (System.nanoTime() >= deadline) { complete = false; break; }
                boolean online = false;
                int latest = 0;
                long itemDeadline = Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(11));
                try {
                    if (row.optBoolean("follow")) latest = latestEpisode(row.getString("seriesId"), itemDeadline);
                    else online = ReservationStatus.published(row.getString("seriesId"), itemDeadline);
                }
                catch (Exception unavailable) { complete = false; }
                if (Thread.currentThread().isInterrupted()) return false;
                store.checked(row, online, latest);
            }
            store.notifyReady(row -> ReservationNotifications.post(context, row));
            return complete && pending.size() <= BATCH;
        } finally {
            RUNNING.unlock();
        }
    }

    private static int latestEpisode(String seriesId, long deadline) throws Exception {
        JSONArray episodes = NativeSeriesDetail.fetch(seriesId, deadline).getJSONArray("episodes");
        JSONObject last = episodes.getJSONObject(episodes.length() - 1);
        return last.getInt("index");
    }
}
