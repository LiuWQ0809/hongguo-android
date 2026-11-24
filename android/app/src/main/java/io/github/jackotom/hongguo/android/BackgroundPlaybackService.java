package io.github.jackotom.hongguo.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public final class BackgroundPlaybackService extends Service {
    private static final String TAKEOVER = "background.takeover";
    private static BackgroundPlaybackSession pending;
    private static volatile BackgroundPlaybackService running;
    private BackgroundPlaybackSession session;
    static boolean begin(Context context, BackgroundPlaybackSession request) {
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
        if (service == null || service.session == null || !service.session.id.equals(id)) return null;
        BackgroundPlaybackSession request = service.session;
        service.stopSelf();
        return request;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && TAKEOVER.equals(intent.getAction()) && pending != null
            && pending.id.equals(intent.getStringExtra("id"))) {
            session = pending;
            pending = null;
            running = this;
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
