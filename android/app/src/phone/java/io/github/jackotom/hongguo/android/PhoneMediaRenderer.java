package io.github.jackotom.hongguo.android;

import android.media.MediaPlayer;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import java.util.Map;
import java.util.function.Consumer;
@UnstableApi
public final class PhoneMediaRenderer implements PlaybackVideoView.Backend, PlaybackEngine {
    private final PlaybackVideoView owner;
    private final ExoPlayer player;
    private final PhoneVideoSurface surface;
    private boolean prepared, seeking, released, ended, buffering;
    private Consumer<PlaybackEngine> onSeek;
    private SizeListener onSize;

    public static PlaybackVideoView.Backend create(PlaybackVideoView owner, boolean software) {
        return software ? new PhoneMediaRenderer(owner, true) : null;
    }

    private PhoneMediaRenderer(PlaybackVideoView owner, boolean software) {
        this.owner = owner;
        var context = owner.getContext();
        surface = new PhoneVideoSurface(context);
        var factory = new DefaultRenderersFactory(context).setEnableDecoderFallback(true);
        if (software) factory.setMediaCodecSelector((mime, secure, tunnel) -> {
            var available = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel);
            return mime.startsWith("video/") ? available.stream().filter(codec -> codec.softwareOnly).collect(java.util.stream.Collectors.toList()) : available;
        });
        player = new ExoPlayer.Builder(context, factory).build();
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onEvents(Player source, Player.Events events) { stateChanged(); }
            @Override public void onVideoSizeChanged(VideoSize size) {
                surface.aspect(size.height == 0 ? 0 : size.width * size.pixelWidthHeightRatio / size.height);
                if (onSize != null) onSize.changed(PhoneMediaRenderer.this, size.width, size.height);
            }
            @Override public void onRenderedFirstFrame() { info(MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START); }
            @Override public void onPlayerError(PlaybackException error) {
                if (!released && owner.onError != null) owner.onError.onEvent(PhoneMediaRenderer.this, MediaPlayer.MEDIA_ERROR_UNSUPPORTED, error.errorCode);
            }
        });
    }

    private void stateChanged() {
        if (released) return;
        int state = player.getPlaybackState();
        boolean nowBuffering = state == Player.STATE_BUFFERING && prepared;
        if (buffering != nowBuffering) {
            buffering = nowBuffering;
            info(buffering ? MediaPlayer.MEDIA_INFO_BUFFERING_START : MediaPlayer.MEDIA_INFO_BUFFERING_END);
        }
        if (state == Player.STATE_ENDED && !ended) {
            ended = true;
            if (owner.onCompletion != null) owner.onCompletion.accept(this);
            return;
        }
        if (state == Player.STATE_READY && getVideoWidth() > 0 && getVideoHeight() > 0) {
            if (!prepared) {
                prepared = true;
                if (owner.onPrepared != null) owner.onPrepared.accept(this);
            }
            if (!released && seeking && onSeek != null) { seeking = false; onSeek.accept(this); }
        }
    }

    private void info(int what) {
        if (!released && owner.onInfo != null) owner.onInfo.onEvent(this, what, 0);
    }
    public PhoneVideoSurface surface() { return surface; }
    public void open(Uri uri, Map<String,String> headers) {
        if (!"file".equals(uri.getScheme()) && !"content".equals(uri.getScheme()))
            throw new IllegalArgumentException("原生解码需要已准备的本地片源");
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
    }
    public void start() { player.play(); }
    public void pause() { player.pause(); }
    public void stop() { released = true; player.release(); }
    public boolean isPlaying() { return player.isPlaying(); }
    public int getDuration() { return millis(player.getDuration()); }
    public int getCurrentPosition() { return millis(player.getCurrentPosition()); }
    private static int millis(long value) { return value == C.TIME_UNSET ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, value)); }
    public void seekTo(int position) { seeking = true; player.seekTo(position); }
    public void seekPrecisely(int position) { seekTo(position); }
    public int getVideoWidth() { return player.getVideoSize().width; }
    public int getVideoHeight() { return player.getVideoSize().height; }
    public void setVolume(float left, float right) { player.setVolume((left + right) * .5f); }
    public float observedSpeed() { return player.getPlaybackParameters().speed; }
    public float setVerifiedSpeed(float rate, boolean playing) {
        PlayerOptions.validateSpeed(rate);
        player.setPlaybackSpeed(rate);
        player.setPlayWhenReady(playing);
        return observedSpeed();
    }
    public void setOnSeekCompleteListener(Consumer<PlaybackEngine> listener) { onSeek = listener; }
    public void setOnVideoSizeChangedListener(SizeListener listener) { onSize = listener; }
}
