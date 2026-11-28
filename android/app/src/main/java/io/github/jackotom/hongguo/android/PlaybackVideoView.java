package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.VideoView;
import java.util.Map;
import java.util.function.Consumer;

/** Owns the video surface while the Activity owns playback policy and controls. */
final class PlaybackVideoView extends FrameLayout {
    interface Event { boolean onEvent(PlaybackEngine engine, int what, int extra); }
    interface Backend {
        SurfaceView surface();
        void open(Uri uri, Map<String,String> headers);
        void start();
        void pause();
        void stop();
        void seekTo(int position);
        boolean isPlaying();
        int getDuration();
        int getCurrentPosition();
    }

    private final VideoView platform;
    private Backend backend;
    private boolean software;
    Consumer<PlaybackEngine> onPrepared, onCompletion;
    Event onInfo, onError;

    PlaybackVideoView(Context context) {
        super(context);
        platform = new VideoView(context);
        platform.setFocusable(false);
        platform.setClickable(false);
        addView(platform, new LayoutParams(-1, -1, Gravity.CENTER));
        platform.setOnPreparedListener(player -> {
            if (onPrepared != null) onPrepared.accept(PlaybackEngine.platform(player));
        });
        platform.setOnCompletionListener(player -> { if (onCompletion != null) onCompletion.accept(null); });
        platform.setOnInfoListener((player, what, extra) -> onInfo != null && onInfo.onEvent(null, what, extra));
        platform.setOnErrorListener((player, what, extra) -> onError != null && onError.onEvent(null, what, extra));
    }

    void configure(boolean software) {
        this.software = software;
    }

    boolean software() { return software; }
    SurfaceView surface() { return backend == null ? platform : backend.surface(); }
    void setOnPreparedListener(Consumer<PlaybackEngine> value) { onPrepared = value; }
    void setOnCompletionListener(Consumer<PlaybackEngine> value) { onCompletion = value; }
    void setOnInfoListener(Event value) { onInfo = value; }
    void setOnErrorListener(Event value) { onError = value; }

    void setVideoURI(Uri uri, Map<String,String> headers) {
        if (backend != null) stopPlayback();
        if (!BuildConfig.IS_TV) backend = advancedBackend();
        if (backend == null) {
            platform.setVisibility(VISIBLE);
            platform.setVideoURI(uri, headers);
            return;
        }
        platform.setVisibility(GONE);
        addView(backend.surface(), new LayoutParams(-1, -1, Gravity.CENTER));
        backend.open(uri, headers);
    }

    private Backend advancedBackend() {
        try {
            return (Backend) Class.forName("io.github.jackotom.hongguo.android.PhoneMediaRenderer")
                .getMethod("create", PlaybackVideoView.class, boolean.class).invoke(null, this, software);
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (software) throw new IllegalStateException("设备软解初始化未完成", failure);
            return null;
        }
    }

    void start() { if (backend == null) platform.start(); else backend.start(); }
    void pause() { if (backend == null) platform.pause(); else backend.pause(); }
    boolean isPlaying() { return backend == null ? platform.isPlaying() : backend.isPlaying(); }
    int getDuration() { return backend == null ? platform.getDuration() : backend.getDuration(); }
    int getCurrentPosition() { return backend == null ? platform.getCurrentPosition() : backend.getCurrentPosition(); }
    void seekTo(int pos) { if (backend == null) platform.seekTo(pos); else backend.seekTo(pos); }
    void stopPlayback() {
        if (backend == null) platform.stopPlayback();
        else {
            Backend old = backend;
            backend = null;
            old.stop();
            removeView(old.surface());
        }
    }
}
