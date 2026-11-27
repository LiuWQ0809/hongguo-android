package io.github.jackotom.hongguo.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.net.Uri;

final class BackgroundPlaybackNotification {
    static final int ID = 18441;
    static final String STOP = "background.stop", PLAY = "background.play", PAUSE = "background.pause";
    static final String PREVIOUS = "background.previous", NEXT = "background.next";
    private static final String CHANNEL = "hongguo_background_playback";
    private BackgroundPlaybackNotification() {}

    static Notification build(Context context, BackgroundPlaybackSession session, MediaSession media) {
        context.getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "后台播放", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setContentTitle("红果后台播放")
            .setOngoing(true)
            .setOnlyAlertOnce(true);
        if (session == null) return builder.build();
        builder.setContentTitle(session.owner().optString("title"))
            .setContentText("第 " + session.episode().optInt("index") + " 集");
        builder.setContentIntent(PendingIntent.getActivity(context, ID, session.reopen,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (session.index > 0) addAction(builder, context, session, PREVIOUS, android.R.drawable.ic_media_previous, "上一集");
        addAction(builder, context, session, session.paused ? PLAY : PAUSE,
            session.paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause, session.paused ? "播放" : "暂停");
        if (session.hasNext()) addAction(builder, context, session, NEXT, android.R.drawable.ic_media_next, "下一集");
        addAction(builder, context, session, STOP, android.R.drawable.ic_menu_close_clear_cancel, "停止");
        if (media != null) {
            int count = 1 + (session.index > 0 ? 1 : 0) + (session.hasNext() ? 1 : 0);
            int[] compact = java.util.stream.IntStream.range(0, count).toArray();
            builder.setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(compact));
        }
        return builder.build();
    }

    private static void addAction(Notification.Builder builder, Context context, BackgroundPlaybackSession session,
                                  String action, int icon, String label) {
        Intent command = new Intent(context, BackgroundPlaybackService.class).setAction(action)
            .setData(Uri.fromParts("hongguo-playback", session.id, null)).putExtra("id", session.id);
        PendingIntent pending = PendingIntent.getService(context, ID, command, PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, icon), label, pending).build());
    }
}
