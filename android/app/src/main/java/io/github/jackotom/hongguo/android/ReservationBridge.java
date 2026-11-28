package io.github.jackotom.hongguo.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationBridge {
    static JSONObject state(Activity activity) throws Exception {
        ReservationStore store = new ReservationStore(activity);
        synchronized (ReservationStore.class) {
            JSONArray rows = store.snapshot();
            for (int i = 0; i < rows.length(); i++) rows.getJSONObject(i).remove("token");
            return new JSONObject().put("items", rows).put("messages", store.messages())
                .put("notificationsAllowed", ReservationNotifications.allowed(activity));
        }
    }

    static JSONObject update(Activity activity, JSONObject args) throws Exception {
        String id = args.getString("seriesId");
        boolean enabled = args.getBoolean("enabled");
        boolean follow = args.optBoolean("follow");
        int latestEpisode = args.optInt("latestEpisode");
        if (enabled && follow && (latestEpisode < 1 || latestEpisode > 10000))
            throw new IllegalArgumentException("请先加载最新分集");
        new ReservationStore(activity).update(id, args.optJSONObject("series"), enabled, follow, latestEpisode);
        if (!enabled) ReservationNotifications.cancel(activity, id, follow);
        ReservationSchedule.plan(activity, true);
        return state(activity);
    }

    static void enableNotifications(Activity activity) {
        if (ReservationNotifications.allowed(activity)) return;
        String permission = Manifest.permission.POST_NOTIFICATIONS;
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            var prefs = activity.getSharedPreferences("reservation_library", Activity.MODE_PRIVATE);
            if (!prefs.getBoolean("askedForNotice", false) || activity.shouldShowRequestPermissionRationale(permission)) {
                prefs.edit().putBoolean("askedForNotice", true).apply();
                activity.requestPermissions(new String[]{permission}, 921);
                return;
            }
        }
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }
}
