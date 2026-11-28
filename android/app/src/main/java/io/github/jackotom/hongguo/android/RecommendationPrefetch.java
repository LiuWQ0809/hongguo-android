package io.github.jackotom.hongguo.android;

import android.os.Handler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.json.JSONObject;

/** One native directory request at a time. A swipe can adopt the speculative request. */
final class RecommendationPrefetch {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main;
    private Future<?> pending;
    private String target;
    private JSONObject ready;
    private Consumer<JSONObject> open;
    private int generation;
    private boolean attempted;
    RecommendationPrefetch(Handler main) { this.main = main; }
    void preload(String seriesId) {
        if (attempted || target != null || seriesId == null) return;
        attempted = true;
        load(seriesId, null);
    }

    void ignoreSelection() { open = null; }

    void load(String seriesId, Consumer<JSONObject> selection) {
        if (seriesId.equals(target)) {
            if (selection != null && ready != null) selection.accept(ready);
            else if (selection != null) open = selection;
            return;
        }
        cancel();
        target = seriesId;
        open = selection;
        int token = generation;
        pending = worker.submit(() -> {
            JSONObject detail = null;
            try { detail = NativeSeriesDetail.fetch(seriesId); }
            catch (Exception ignored) { /* The current work stays playable. */ }
            JSONObject result = detail;
            main.post(() -> {
                if (token != generation) return;
                Consumer<JSONObject> selected = open;
                if (result == null) cancel();
                else { ready = result; pending = null; open = null; }
                if (selected != null) selected.accept(result);
            });
        });
    }
    void cancel() {
        ++generation;
        if (pending != null) pending.cancel(true);
        pending = null; target = null; ready = null; open = null;
    }
    void close() { cancel(); worker.shutdownNow(); }
}
