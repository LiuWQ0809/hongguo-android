package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Offline queue persistence independent of platform downloads created by earlier versions. */
final class OfflineDownloadJournal {
    final Map<Long,OfflineDownloadJob> jobs = new LinkedHashMap<>();
    final SharedPreferences prefs;
    final LibraryStore library;
    final File directory;
    final File work;

    OfflineDownloadJournal(Context context) throws Exception {
        prefs = context.getSharedPreferences("offline_downloads_v1", Context.MODE_PRIVATE);
        library = new LibraryStore(context);
        directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null || !directory.isDirectory() || LegacyFiles.isSymbolicLink(directory))
            throw new IOException("本机下载目录不可用");
        work = new File(directory, ".offline-work");
        if (!work.isDirectory() && !work.mkdir() || LegacyFiles.isSymbolicLink(work))
            throw new IOException("下载续传目录不可用");
        for (var entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("job_") || !(entry.getValue() instanceof String value)) continue;
            try {
                OfflineDownloadJob job = OfflineDownloadJob.restore(new JSONObject(value));
                if (!entry.getKey().equals(key(job))) continue;
                if (library.download(job.id) != null) clean(job);
                else jobs.put(job.id, job);
            } catch (Exception invalid) { android.util.Log.w("HongguoDownload", "本地下载任务需要重新读取"); }
        }
    }

    private static String key(OfflineDownloadJob job) { return "job_" + job.id; }

    void save(OfflineDownloadJob job, String state) throws Exception {
        String before = job.status;
        job.status = state;
        try {
            if (!prefs.edit().putString(key(job), job.snapshot().toString()).commit()) throw new IOException("下载状态保存未完成");
        } catch (Exception failure) { job.status = before; throw failure; }
    }

    OfflineRange range(OfflineDownloadJob job) { return new OfflineRange(job.part(work), prefs, job.id); }

    void clean(OfflineDownloadJob job) throws Exception {
        if (!LegacyFiles.deleteIfExists(job.staging(work)) || !LegacyFiles.deleteIfExists(job.part(work)))
            throw new IOException("下载中间文件清理未完成");
        range(job).forget();
        if (!prefs.edit().remove(key(job)).commit()) throw new IOException("下载任务清理未完成");
        jobs.remove(job.id);
    }

    void publish(OfflineDownloadJob job) throws Exception {
        File target = job.finished(directory);
        if (LegacyFiles.isSymbolicLink(target)) throw new IOException("下载目标文件归属异常");
        if (!job.staging(work).renameTo(target)) throw new IOException("下载结果保存未完成");
        try {
            library.addPreparedDownload(job.id, job.series, job.episode, directory, target);
        } catch (Exception failed) {
            if (!LegacyFiles.deleteIfExists(target)) failed.addSuppressed(new IOException("未登记的视频文件清理未完成"));
            throw failed;
        }
        clean(job);
    }

    void delete(OfflineDownloadJob job) throws Exception {
        if (library.download(job.id) != null) throw new IOException("下载已完成，请刷新后删除文件");
        if (!LegacyFiles.deleteIfExists(job.finished(directory))) throw new IOException("下载结果文件清理未完成");
        clean(job);
    }
}
