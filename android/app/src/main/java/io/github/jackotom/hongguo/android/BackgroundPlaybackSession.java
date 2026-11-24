package io.github.jackotom.hongguo.android;

import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** The service owns this snapshot; only the main thread advances or hands it back. */
final class BackgroundPlaybackSession {
    final String id, quality;
    final JSONObject series;
    final List<JSONObject> queue;
    final Intent reopen;
    final float speed;
    final int ceiling;
    final boolean autoplay, cleanup, offline;
    Uri preparedUri;
    int index, position;
    boolean paused, completed;

    BackgroundPlaybackSession(String id, Intent reopen, JSONObject series, List<JSONObject> queue,
                              int index, int position, String quality, int ceiling, float speed,
                              boolean autoplay, boolean cleanup, boolean offline, Uri preparedUri) {
        this.id = id;
        this.reopen = new Intent(reopen).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        this.series = series;
        this.queue = Collections.unmodifiableList(new ArrayList<>(queue));
        this.index = index;
        this.position = Math.max(0, position);
        this.quality = quality;
        this.ceiling = ceiling;
        this.speed = speed;
        this.autoplay = autoplay;
        this.cleanup = cleanup;
        this.offline = offline;
        this.preparedUri = preparedUri;
    }

    JSONObject episode() { return queue.get(index); }
    JSONObject owner() { return PlaybackPlaylist.owner(series, episode()); }
    boolean hasNext() { return index + 1 < queue.size(); }
}
