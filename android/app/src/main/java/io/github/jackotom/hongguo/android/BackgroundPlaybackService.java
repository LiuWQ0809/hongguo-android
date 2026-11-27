package io.github.jackotom.hongguo.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.Uri;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class BackgroundPlaybackService extends Service {
    private static final String TAKEOVER = "background.takeover";
    private static BackgroundPlaybackSession pending, parked;
    private static volatile BackgroundPlaybackService running;
    private volatile BackgroundPlaybackSession session;
    private BackgroundEpisodeSource source;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService resolver = Executors.newSingleThreadExecutor();
    private Future<?> preparing;
    private MediaPlayer player;
    private MediaSession media;
    private PowerManager.WakeLock preparingWake;
    private final Runnable preparationTimeout = () -> { if (!this.ready) park(); };
    private LibraryStore store;
    private boolean ready;
    private int loadGeneration;
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            savePosition();
            updateMediaState();
            if (ready) main.postDelayed(this, 2000);
        }
    };
    static boolean begin(Context context, BackgroundPlaybackSession request) {
        parked = null;
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
        if (service == null || service.session == null || !service.session.id.equals(id)) {
            if (parked == null || !parked.id.equals(id)) return null;
            BackgroundPlaybackSession request = parked;
            parked = null;
            return request;
        }
        service.savePosition();
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
        if (parked != null && !parked.id.equals(id)) parked = null;
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
            park();
        } else if (TAKEOVER.equals(action) && pending != null && pending.id.equals(intent.getStringExtra("id"))) {
            clearSession();
            session = pending;
            pending = null;
            running = this;
            source = new BackgroundEpisodeSource(this, session);
            store = new LibraryStore(this);
            openControls();
        }
        startForeground(BackgroundPlaybackNotification.ID, BackgroundPlaybackNotification.build(this, session, media));
        if (session == null) stopSelf(startId);
        else if (player == null && preparing == null && !session.completed) load();
        return START_NOT_STICKY;
    }
    private void load() {
        acquirePreparationWake();
        BackgroundPlaybackSession request = session;
        int generation = ++loadGeneration;
        BackgroundEpisodeSource files = source;
        var owner = request.owner();
        var episode = request.episode();
        Uri prepared = request.preparedUri;
        request.preparedUri = null;
        preparing = resolver.submit(() -> {
            try {
                Uri uri = files.load(request, owner, episode, prepared);
                main.post(() -> { if (session == request && loadGeneration == generation) open(uri, request); });
            } catch (Exception failed) {
                main.post(() -> { if (session == request && loadGeneration == generation) park(); });
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
                try { PlayerOptions.setVerifiedSpeed(ready, request.speed, false); }
                catch (Exception rejectedSpeed) { android.util.Log.w("HongguoBackground", "设备未确认后台倍速"); }
                if (!request.paused) ready.start();
                this.ready = true;
                releasePreparationWake();
                updateMediaState();
                android.util.Log.i("HongguoBackground", "第 " + request.episode().optInt("index") + " 集开始后台播放");
                main.postDelayed(checkpoint, 2000);
            });
            player.setOnCompletionListener(done -> { if (done == player) completed(); });
            player.setOnErrorListener((failed, what, extra) -> { if (failed == player) park(); return true; });
            player.prepareAsync();
        } catch (Exception unavailable) {
            park();
        }
    }

    private void completed() {
        session.completed = true;
        if (!session.autoplay || !session.hasNext()) { park(); return; }
        switchEpisode(1);
    }

    private void switchEpisode(int delta) {
        if (session == null) return;
        int next = session.index + delta;
        if (next < 0 || next >= session.queue.size()) return;
        savePosition();
        if (preparing != null) preparing.cancel(true);
        preparing = null;
        releaseDecoder();
        session.index = next;
        session.position = 0;
        session.completed = false;
        source.select(next);
        startForeground(BackgroundPlaybackNotification.ID, BackgroundPlaybackNotification.build(this, session, media));
        updateMediaState();
        load();
    }

    private void openControls() {
        media = new MediaSession(this, "HongguoBackgroundPlayback");
        media.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { setPaused(false); }
            @Override public void onPause() { setPaused(true); }
            @Override public void onSkipToPrevious() { switchEpisode(-1); }
            @Override public void onSkipToNext() { switchEpisode(1); }
            @Override public void onStop() { park(); }
        }, main);
        media.setActive(true);
        updateMediaState();
    }

    private void setPaused(boolean paused) {
        if (session == null || session.completed) return;
        session.paused = paused;
        if (ready) { if (paused) player.pause(); else player.start(); }
        savePosition();
        updateMediaState();
    }

    private void updateMediaState() {
        if (media == null || session == null) return;
        String title = session.owner().optString("title") + " · 第 " + session.episode().optInt("index") + " 集";
        media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build());
        int state = session.completed ? PlaybackState.STATE_STOPPED : session.paused ? PlaybackState.STATE_PAUSED
            : ready ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_BUFFERING;
        long actions = PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP;
        if (session.index > 0) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (session.hasNext()) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        media.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, session.position, state == PlaybackState.STATE_PLAYING ? session.speed : 0).build());
    }

    private void savePosition() {
        if (!ready || session == null) return;
        try {
            session.position = session.completed ? 0 : Math.max(0, player.getCurrentPosition());
            store.history(session.owner(), session.episode(), session.position, player.getDuration());
        } catch (IllegalStateException invalidDecoder) {
            // Preserve the previous checkpoint after the decoder reports an error.
        }
    }

    private void releaseDecoder() {
        releasePreparationWake();
        ready = false;
        main.removeCallbacks(checkpoint);
        if (player != null) player.release();
        player = null;
    }

    private void acquirePreparationWake() {
        releasePreparationWake();
        preparingWake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hongguo:PreparingEpisode");
        preparingWake.acquire(100_000L);
        main.postDelayed(preparationTimeout, 95_000L);
    }

    private void releasePreparationWake() {
        main.removeCallbacks(preparationTimeout);
        if (preparingWake != null && preparingWake.isHeld()) preparingWake.release();
        preparingWake = null;
    }

    private void park() {
        if (session != null) {
            session.paused = true;
            parked = session;
        }
        clearSession();
        stopSelf();
    }

    private void clearSession() {
        savePosition();
        releaseDecoder();
        if (preparing != null) preparing.cancel(true);
        preparing = null;
        if (media != null) media.release();
        media = null;
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
