package io.github.jackotom.hongguo.android;

import android.content.Context;
import java.util.ArrayList;
import java.util.Comparator;
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
                if (!row.optBoolean("online")) pending.add(row);
            }
            pending.sort(Comparator.comparingLong(row -> row.optLong("checkedAt")));
            boolean complete = true;
            for (JSONObject row : pending.subList(0, Math.min(BATCH, pending.size()))) {
                if (Thread.currentThread().isInterrupted()) return false;
                boolean online = false;
                try { online = ReservationStatus.published(row.getString("seriesId")); }
                catch (Exception unavailable) { complete = false; }
                if (Thread.currentThread().isInterrupted()) return false;
                store.checked(row, online);
            }
            store.notifyReady(row -> ReservationNotifications.post(context, row));
            return complete && pending.size() <= BATCH;
        } finally {
            RUNNING.unlock();
        }
    }
}
