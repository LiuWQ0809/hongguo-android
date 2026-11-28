package io.github.jackotom.hongguo.android;

import android.content.Context;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;
final class OfflineDownloadQueue {
    private static final long FIRST_ID = 4_000_000_000_000L;
    private static OfflineDownloadQueue instance;
    private final OfflineDownloadJournal data;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    static synchronized OfflineDownloadQueue get(Context context) throws Exception {
        if (instance == null) instance = new OfflineDownloadQueue(context.getApplicationContext());
        return instance;
    }

    private OfflineDownloadQueue(Context context) throws Exception {
        data = new OfflineDownloadJournal(context);
        pump();
    }

    synchronized long enqueue(JSONObject series, JSONObject episode, PlaybackSources.Plan plan) throws Exception {
        if (data.jobs.size() + data.library.downloads().length() >= 200) throw new IOException("下载记录已达 200 条上限");
        long id = FIRST_ID + System.currentTimeMillis();
        while (data.jobs.containsKey(id) || data.library.download(id) != null) id++;
        var job = new OfflineDownloadJob(id, series, episode, plan.requestedQuality);
        job.plan = plan;
        data.save(job, "pending"); data.jobs.put(id, job); pump();
        return id;
    }

    synchronized JSONArray rows() throws Exception {
        JSONArray rows = new JSONArray();
        for (OfflineDownloadJob job : data.jobs.values()) {
            if (!"deleting".equals(job.status) && data.library.download(job.id) == null) rows.put(job.row(data.work));
        }
        return rows;
    }

    synchronized void control(long id, String action) throws Exception {
        OfflineDownloadJob job = data.jobs.get(id);
        if (job == null || "deleting".equals(job.status)) throw new IOException("下载任务已变更，请刷新");
        if ("pause".equals(action)) {
            data.save(job, "paused");
            if (job.transfer != null) job.transfer.cancel();
        } else if ("resume".equals(action)) {
            if ("paused".equals(job.status) || "failed".equals(job.status)) data.save(job, "pending");
            pump();
        } else throw new IOException("下载任务操作无效");
    }

    synchronized boolean delete(long id) throws Exception {
        OfflineDownloadJob job = data.jobs.get(id);
        if (job == null || data.library.download(id) != null) return false;
        data.save(job, "deleting");
        if (job.transfer != null) job.transfer.cancel();
        else data.delete(job);
        return true;
    }

    private synchronized void pump() {
        long active = data.jobs.values().stream().filter(job -> job.transfer != null).count();
        for (OfflineDownloadJob job : new java.util.ArrayList<>(data.jobs.values())) {
            if (job.transfer == null && "deleting".equals(job.status)) {
                try { data.delete(job); } catch (Exception failed) { logFailure(failed); }
            }
            if (active >= 2 || job.transfer != null || !"pending".equals(job.status)) continue;
            job.transfer = new OfflineTransfer();
            active++;
            workers.execute(() -> run(job));
        }
    }

    private void run(OfflineDownloadJob job) {
        try (var lease = UpdateInstallCheckpoint.operation()) {
            synchronized (this) {
                if (!"pending".equals(job.status)) return;
                data.save(job, "running");
            }
            OfflineDownloadWorker.prepare(data, job, job.transfer);
            synchronized (this) { if ("running".equals(job.status)) data.publish(job); }
        } catch (Exception failure) {
            synchronized (this) {
                try { if ("running".equals(job.status) || "pending".equals(job.status) && !job.transfer.cancelled()) data.save(job, "failed"); }
                catch (Exception persistence) { logFailure(persistence); }
            }
            if (!job.transfer.cancelled()) logFailure(failure);
        } finally {
            synchronized (this) {
                LegacyFiles.deleteIfExists(job.staging(data.work));
                job.transfer = null;
                pump();
            }
        }
    }

    private static void logFailure(Exception failure) { android.util.Log.w("HongguoDownload", failure.getClass().getSimpleName()); }
}
