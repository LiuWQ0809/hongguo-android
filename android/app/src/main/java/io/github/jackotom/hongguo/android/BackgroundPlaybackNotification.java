package io.github.jackotom.hongguo.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;

final class BackgroundPlaybackNotification {
    static final int ID = 18441;
    static final String STOP = "background.stop";
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
        if (session != null) {
            builder.setContentTitle(session.owner().optString("title"))
                .setContentText("第 " + session.episode().optInt("index") + " 集");
            builder.setContentIntent(PendingIntent.getActivity(context, ID, session.reopen,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        if (media != null) builder.setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()));
        Intent stop = new Intent(context, BackgroundPlaybackService.class).setAction(STOP);
        PendingIntent dismiss = PendingIntent.getService(context, ID, stop, PendingIntent.FLAG_IMMUTABLE);
        return builder.addAction(new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
            "停止", dismiss).build()).build();
    }
}
