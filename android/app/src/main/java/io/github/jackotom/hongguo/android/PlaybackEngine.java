package io.github.jackotom.hongguo.android;

import android.media.MediaPlayer;

/** Decoder operations used by the shared native playback controller. */
interface PlaybackEngine {
    int getVideoWidth();
    int getVideoHeight();
    boolean isPlaying();
    void pause();
    void setVolume(float left, float right);
    void seekPrecisely(int positionMs);
    float observedSpeed();
    float setVerifiedSpeed(float rate, boolean playing) throws Exception;
    void setOnSeekCompleteListener(java.util.function.Consumer<PlaybackEngine> listener);
    void setOnVideoSizeChangedListener(SizeListener listener);

    interface SizeListener { void changed(PlaybackEngine engine, int width, int height); }

    static PlaybackEngine platform(MediaPlayer player) {
        return new PlaybackEngine() {
            public int getVideoWidth() { return player.getVideoWidth(); }
            public int getVideoHeight() { return player.getVideoHeight(); }
            public boolean isPlaying() { return player.isPlaying(); }
            public void pause() { player.pause(); }
            public void setVolume(float left, float right) { player.setVolume(left, right); }
            public void seekPrecisely(int positionMs) {
                if (android.os.Build.VERSION.SDK_INT >= 26) player.seekTo((long) positionMs, MediaPlayer.SEEK_CLOSEST);
                else player.seekTo(positionMs);
            }
            public float observedSpeed() { return PlayerOptions.observedSpeed(player); }
            public float setVerifiedSpeed(float rate, boolean playing) throws Exception {
                return PlayerOptions.setVerifiedSpeed(player, rate, playing);
            }
            public void setOnSeekCompleteListener(java.util.function.Consumer<PlaybackEngine> listener) {
                player.setOnSeekCompleteListener(listener == null ? null : ignored -> listener.accept(this));
            }
            public void setOnVideoSizeChangedListener(SizeListener listener) {
                player.setOnVideoSizeChangedListener(listener == null ? null : (ignored, width, height) -> listener.changed(this, width, height));
            }
        };
    }
}
