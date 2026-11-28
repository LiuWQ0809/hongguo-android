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
import org.json.JSONException;
import org.json.JSONObject;

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
    private PlaybackCuts cuts = PlaybackCuts.DEFAULT;
    private int loadGeneration;
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            savePosition();
            if (ready && session != null && !session.paused && player.isPlaying()
                    && cuts.finishesAt(session.position, player.getDuration())) {
                player.pause(); completed(); return;
            }
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

    // The page bridge and service callbacks share the Android main thread.
    static JSONObject controls(Context context, JSONObject params) throws Exception {
        BackgroundPlaybackService service = running;
        BackgroundPlaybackSession current = service == null ? null : service.session;
        if (current == null) return new JSONObject();
        if (params != null) {
            if (!current.id.equals(params.optString("id"))) throw new JSONException("当前播放已切换");
            switch (params.getString("action")) {
                case "open": context.startActivity(new Intent(current.reopen)); break;
                case "play": service.setPaused(false); break;
                case "pause": service.setPaused(true); break;
                case "previous": service.switchEpisode(-1); break;
                case "next": service.switchEpisode(1); break;
                case "stop": service.park(); return new JSONObject();
                default: throw new JSONException("播放操作无效");
            }
        }
        return new JSONObject().put("id", current.id).put("title", current.owner().getString("title"))
            .put("index", current.episode().getInt("index")).put("paused", current.paused)
            .put("previous", current.index > 0).put("next", current.hasNext())
            .put("ready", service.ready).put("positionMs", current.position);
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
        if (!TAKEOVER.equals(action)) {
            if (action != null && session != null && session.id.equals(intent.getStringExtra("id"))) {
                switch (action) {
                    case BackgroundPlaybackNotification.PLAY: setPaused(false); break;
                    case BackgroundPlaybackNotification.PAUSE: setPaused(true); break;
                    case BackgroundPlaybackNotification.PREVIOUS: switchEpisode(-1); break;
                    case BackgroundPlaybackNotification.NEXT: switchEpisode(1); break;
                    case BackgroundPlaybackNotification.STOP: park(); break;
                }
            }
            if (session == null) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (pending != null && pending.id.equals(intent.getStringExtra("id"))) {
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
        else if (player == null && preparing == null) {
            if (!session.completed) load();
            else if (!advanceSeason()) park();
        }
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
        cuts = PlaybackCuts.read(this);
        try {
            player = new MediaPlayer();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, uri);
            player.setOnPreparedListener(ready -> {
                if (ready != player) return;
                request.position = cuts.start(request.position, ready.getDuration());
                if (request.position > 0) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) ready.seekTo((long) request.position, MediaPlayer.SEEK_CLOSEST);
                    else ready.seekTo(request.position);
                }
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
        if (ready && session != null) {
            session.position = Math.max(0, player.getDuration());
            store.history(session.owner(), session.episode(), session.position, session.position);
        }
        session.completed = true;
        if (session.autoplay && session.hasNext()) switchEpisode(1);
        else if (!advanceSeason()) park();
    }

    private boolean advanceSeason() {
        BackgroundPlaybackSession before = session;
        if (BuildConfig.IS_TV || !before.autoplay || before.offline || !SeasonContinuation.atFinale(before.owner(), before.episode())) return false;
        releaseDecoder();
        before.advancing = true;
        acquirePreparationWake();
        updateMediaState();
        int token = ++loadGeneration;
        preparing = resolver.submit(() -> {
            SeasonContinuation resolved = null;
            try { resolved = SeasonContinuation.load(this, before.owner(), before.episode()); }
            catch (Exception ignored) { /* Preserve the completed season when the next directory is unavailable. */ }
            SeasonContinuation season = resolved;
            main.post(() -> acceptSeason(before, season, token));
        });
        return true;
    }

    private void acceptSeason(BackgroundPlaybackSession before, SeasonContinuation season, int token) {
        if (session != before || loadGeneration != token) return;
        before.advancing = false;
        preparing = null;
        if (season == null) { park(); return; }
        try {
            BackgroundPlaybackSession next = before.nextSeason(this, season);
            BackgroundEpisodeSource nextSource = new BackgroundEpisodeSource(this, next);
            source.close();
            source = nextSource;
            session = next;
            startForeground(BackgroundPlaybackNotification.ID, BackgroundPlaybackNotification.build(this, next, media));
            updateMediaState();
            load();
        } catch (Exception unavailable) { park(); }
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
        session.completed = session.advancing = false;
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
        if (session == null || session.completed && !session.advancing || session.paused == paused) return;
        session.paused = paused;
        if (ready) { if (paused) player.pause(); else player.start(); }
        savePosition();
        updateMediaState();
        startForeground(BackgroundPlaybackNotification.ID, BackgroundPlaybackNotification.build(this, session, media));
    }

    private void updateMediaState() {
        if (media == null || session == null) return;
        String title = session.owner().optString("title") + " · 第 " + session.episode().optInt("index") + " 集";
        media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build());
        int state = session.paused ? PlaybackState.STATE_PAUSED : session.advancing ? PlaybackState.STATE_BUFFERING : session.completed ? PlaybackState.STATE_STOPPED
            : ready ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_BUFFERING;
        long actions = PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP;
        if (session.index > 0) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (session.hasNext()) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        media.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, session.position, state == PlaybackState.STATE_PLAYING ? session.speed : 0).build());
    }

    private void savePosition() {
        if (!ready || session == null || session.completed) return;
        try {
            session.position = Math.max(0, player.getCurrentPosition());
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
            session.advancing = false;
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
