package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

/** Uses the native resolver and the same verified file cache as the on-screen player. */
final class BackgroundEpisodeSource implements AutoCloseable {
    private final Context context;
    private final PlaybackCache cache;
    private final PlaybackCache.QueueWindow window;

    BackgroundEpisodeSource(Context context, BackgroundPlaybackSession session) {
        this.context = context;
        cache = PlaybackCache.get(context);
        window = session.offline ? null : cache.openQueueWindow(session.series, session.queue,
            session.index, session.cleanup, 1, false);
    }

    void select(int index) { if (window != null) window.moveTo(index); }

    Uri load(BackgroundPlaybackSession session, JSONObject owner, JSONObject episode, Uri prepared) throws Exception {
        if (prepared != null) return prepared;
        String sid = owner.getString("seriesId"), vid = episode.getString("vid");
        int index = episode.getInt("index");
        if (session.offline || "auto".equals(session.quality)) {
            Uri local = LocalDownloadLookup.find(context, sid, vid);
            if (local != null) return local;
        }
        if (session.offline) throw new IOException("下一集尚未下载");
        File file = cache.find(sid, vid, index, session.quality, session.ceiling);
        if (file == null) {
            PlaybackSources.Plan plan = PlaybackSources.resolvePlayback(sid, vid, session.quality,
                session.ceiling, false, false, RegisteredPlaybackLookup::resolve);
            file = cache.fetchForeground(sid, vid, index, plan, session.quality);
        }
        return Uri.fromFile(file);
    }

    @Override public void close() { if (window != null) window.close(); }
}
