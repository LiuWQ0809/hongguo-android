package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class PipPlaybackControls {
    private final MediaSession session;

    PipPlaybackControls(Context context, Consumer<Boolean> setPaused, IntConsumer skip) {
        session = new MediaSession(context, "HongguoPictureInPicture");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { setPaused.accept(false); }
            @Override public void onPause() { setPaused.accept(true); }
            @Override public void onSkipToPrevious() { skip.accept(-1); }
            @Override public void onSkipToNext() { skip.accept(1); }
        });
        session.setActive(true);
    }

    void update(String title, int index, int size, boolean paused, boolean ready, int position, float speed) {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE;
        if (index > 0) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (index + 1 < size) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        int state = paused ? PlaybackState.STATE_PAUSED : ready ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_BUFFERING;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
            .setState(state, position, state == PlaybackState.STATE_PLAYING ? speed : 0).build());
    }

    void close() { session.release(); }
}
