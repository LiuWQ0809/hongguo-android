package com.hongguo.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object UpdateNotifications {
    const val ACTION_CHECK = "com.hongguo.android.CHECK_UPDATE"
    const val NOTIFICATION_ID = 2308
    private const val CHANNEL = "app_updates"

    fun post(context: Context, update: AppUpdate): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        if (preferences.getLong("notified_version", 0) >= update.versionCode) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "软件更新", NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION_CHECK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 2308, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("红果独立版有新版本").setContentText("发现 ${update.versionName}，点按查看更新")
            .setContentIntent(open).setAutoCancel(true).build()
        manager.notify(NOTIFICATION_ID, notification)
        preferences.edit().putLong("notified_version", update.versionCode).apply()
        return true
    }
}
