package io.github.jackotom.hongguo.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONObject;

final class ReservationNotifications {
    static final String CHANNEL = "drama_reservations";
    private static final int NOTICE = 31;

    static boolean allowed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "短剧开播提醒", NotificationManager.IMPORTANCE_DEFAULT));
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static boolean post(Context context, JSONObject row) {
        if (!allowed(context)) return false;
        String id = row.optString("seriesId");
        Intent detail = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://hongguoduanju.com/detail?series_id=" + id), context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, detail, PendingIntent.FLAG_IMMUTABLE);
        Notification watch = new Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("预约的短剧已上线").setContentText(row.optString("title"))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build();
        try {
            context.getSystemService(NotificationManager.class).notify(id, NOTICE, watch);
            return true;
        } catch (SecurityException denied) { return false; }
    }

    static void cancel(Context context, String id) {
        context.getSystemService(NotificationManager.class).cancel(id, NOTICE);
    }
}
