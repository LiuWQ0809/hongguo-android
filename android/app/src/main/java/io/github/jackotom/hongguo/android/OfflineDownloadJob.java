package io.github.jackotom.hongguo.android;

import android.media.MediaExtractor;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.json.JSONObject;

final class OfflineDownloadJob {
    final long id;
    final JSONObject series;
    final JSONObject episode;
    final String quality;
    final UUID fileId;
    String status;
    OfflineTransfer transfer;
    PlaybackSources.Plan plan;

    OfflineDownloadJob(long id, JSONObject series, JSONObject episode, String quality) {
        this(id, series, episode, quality, UUID.randomUUID(), "pending");
    }

    private OfflineDownloadJob(long id, JSONObject series, JSONObject episode, String quality, UUID fileId, String status) {
        this.id = id; this.series = series; this.episode = episode; this.quality = quality;
        this.fileId = fileId; this.status = status;
    }

    static OfflineDownloadJob restore(JSONObject saved) throws Exception {
        String status = saved.getString("status");
        if (status.equals("running")) status = "pending";
        if (!java.util.Set.of("pending", "paused", "failed", "deleting").contains(status)) throw new IOException("下载状态无效");
        return new OfflineDownloadJob(saved.getLong("id"), LibraryStore.series(saved.getJSONObject("series")),
            LibraryStore.episode(saved.getJSONObject("episode")), MediaQuality.normalize(saved.getString("quality")),
            UUID.fromString(saved.getString("fileId")), status);
    }

    JSONObject snapshot() throws Exception {
        return new JSONObject().put("id", id).put("series", series).put("episode", episode)
            .put("quality", quality).put("status", status).put("fileId", fileId.toString());
    }

    JSONObject row(File work) throws Exception {
        return new JSONObject().put("id", id).put("seriesId", series.getString("seriesId"))
            .put("title", series.getString("title")).put("index", episode.getInt("index"))
            .put("status", status).put("downloadedBytes", part(work).length())
            .put("totalBytes", transfer == null ? -1 : transfer.total).put("canControl", true);
    }

    File part(File work) { return new File(work, fileId + ".part"); }
    File staging(File work) { return new File(work, fileId + ".staging"); }
    File finished(File directory) {
        return new File(directory, series.optString("seriesId") + "_" + episode.optInt("index") + "_" + fileId + ".mp4");
    }

    void prepare(PlaybackSources.Plan plan, File work) throws Exception {
        if (plan.guestPlayback) PublicPlaybackVerifier.verify(part(work), plan.expectedDurationSeconds, plan.width, plan.height);
        LegacyFiles.copy(part(work), staging(work), true);
        if (plan.contentKey != null) {
            byte[] key = new byte[16];
            for (int i = 0; i < key.length; i++) key[i] = (byte) Integer.parseInt(plan.contentKey.substring(i * 2, i * 2 + 2), 16);
            try { CencDecryptor.decryptInPlace(staging(work), key); }
            finally { java.util.Arrays.fill(key, (byte) 0); }
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(staging(work).getPath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) return;
            }
            throw new IOException("下载文件缺少可识别的视频轨道");
        } finally { extractor.release(); }
    }
}
