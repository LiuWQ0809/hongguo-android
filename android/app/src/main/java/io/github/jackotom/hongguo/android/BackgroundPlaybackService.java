package io.github.jackotom.hongguo.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.Uri;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class BackgroundPlaybackService extends Service {
    private static final String TAKEOVER = "background.takeover";
    private static BackgroundPlaybackSession pending;
    private static volatile BackgroundPlaybackService running;
    private volatile BackgroundPlaybackSession session;
    private BackgroundEpisodeSource source;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService resolver = Executors.newSingleThreadExecutor();
    private Future<?> preparing;
    private MediaPlayer player;
    static boolean begin(Context context, BackgroundPlaybackSession request) {
        pending = request;
        try {
            context.startForegroundService(new Intent(context, BackgroundPlaybackService.class)
                .setAction(TAKEOVER).putExtra("id", request.id));
            return true;
        } catch (RuntimeException unavailable) {
            pending = null;
            return false;
        }
    }

    static BackgroundPlaybackSession take(String id) {
        if (pending != null && pending.id.equals(id)) {
            BackgroundPlaybackSession request = pending;
            pending = null;
            return request;
        }
        BackgroundPlaybackService service = running;
        if (service == null || service.session == null || !service.session.id.equals(id)) return null;
        BackgroundPlaybackSession request = service.session;
        service.clearSession();
        service.stopSelf();
        return request;
    }

    static boolean isActive() {
        BackgroundPlaybackService service = running;
        return service != null && service.session != null;
    }

    static void stopOther(String id) {
        if (pending != null && !pending.id.equals(id)) pending = null;
        BackgroundPlaybackService service = running;
        if (service != null && service.session != null && !service.session.id.equals(id)) {
            service.clearSession();
            service.stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (BackgroundPlaybackNotification.STOP.equals(action)) {
            clearSession();
        } else if (TAKEOVER.equals(action) && pending != null && pending.id.equals(intent.getStringExtra("id"))) {
            clearSession();
            session = pending;
            pending = null;
            running = this;
            source = new BackgroundEpisodeSource(this, session);
        }
        startForeground(BackgroundPlaybackNotification.ID, BackgroundPlaybackNotification.build(this, session, null));
        if (session == null) stopSelf(startId);
        else if (player == null && preparing == null) load();
        return START_NOT_STICKY;
    }
    private void load() {
        BackgroundPlaybackSession request = session;
        BackgroundEpisodeSource files = source;
        var owner = request.owner();
        var episode = request.episode();
        Uri prepared = request.preparedUri;
        request.preparedUri = null;
        preparing = resolver.submit(() -> {
            try {
                Uri uri = files.load(request, owner, episode, prepared);
                main.post(() -> { if (session == request) open(uri, request); });
            } catch (Exception failed) {
                main.post(() -> { if (session == request) stopSelf(); });
            }
        });
    }
    private void open(Uri uri, BackgroundPlaybackSession request) {
        preparing = null;
        try {
            player = new MediaPlayer();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, uri);
            player.setOnPreparedListener(ready -> {
                if (ready != player) return;
                if (request.position > 0) ready.seekTo(request.position);
                if (!request.paused) ready.start();
            });
            player.prepareAsync();
        } catch (Exception unavailable) {
            stopSelf();
        }
    }

    private void clearSession() {
        if (preparing != null) preparing.cancel(true);
        preparing = null;
        if (player != null) player.release();
        player = null;
        if (source != null) source.close();
        source = null;
        session = null;
        if (running == this) running = null;
    }

    @Override public void onDestroy() {
        clearSession();
        resolver.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
