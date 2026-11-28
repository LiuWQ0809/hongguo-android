package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class OfflineDownloadWorker {
    private OfflineDownloadWorker() { }

    static void prepare(OfflineDownloadJournal journal, OfflineDownloadJob job, OfflineTransfer transfer) throws Exception {
        if (LegacyFiles.isSymbolicLink(job.part(journal.work)) || LegacyFiles.isSymbolicLink(job.staging(journal.work)))
            throw new IOException("下载断点文件归属异常");
        PlaybackSources.Plan initial = job.plan;
        job.plan = null;
        if (initial == null) initial = PlaybackSources.resolve(job.series.getString("seriesId"), job.episode.getString("vid"), job.quality);
        List<PlaybackSources.Plan> plans = new ArrayList<>();
        plans.add(initial);
        plans.addAll(initial.variants);
        Exception last = null;
        for (PlaybackSources.Plan plan : plans) {
            try {
                transfer.fetch(plan, job.part(journal.work), journal.range(job));
                job.prepare(plan, journal.work);
                return;
            } catch (Exception failure) {
                if (transfer.cancelled() || plan.guestPlayback || !PlaybackSources.canRecoverPlayback(failure)) throw failure;
                last = failure;
            }
        }
        throw last == null ? new IOException("下载片源未返回") : last;
    }
}
