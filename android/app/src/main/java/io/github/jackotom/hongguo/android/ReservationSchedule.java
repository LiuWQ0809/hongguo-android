package io.github.jackotom.hongguo.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReservationSchedule {
    static final int PERIODIC = 8610;
    static final int FOREGROUND = 8611;

    static synchronized void plan(Context context, boolean foreground) {
        try {
            if (BuildConfig.IS_TV) return;
            JSONArray rows = new ReservationStore(context).snapshot();
            boolean pending = false, due = false, waitingNotice = false;
            long limit = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!row.optBoolean("online") || row.optBoolean("follow")) {
                    pending = true;
                    due |= row.optLong("checkedAt") <= limit;
                }
                waitingNotice |= row.optBoolean("online") && !row.optBoolean("notified");
            }
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            ComponentName job = new ComponentName(context, ReservationJob.class);
            if (pending && scheduler.getPendingJob(PERIODIC) == null) {
                scheduler.schedule(new JobInfo.Builder(PERIODIC, job).setPersisted(true)
                    .setPeriodic(TimeUnit.HOURS.toMillis(1)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
            } else if (!pending) scheduler.cancel(PERIODIC);
            boolean usefulForeground = due || waitingNotice && ReservationNotifications.allowed(context);
            if (foreground && usefulForeground && scheduler.getPendingJob(FOREGROUND) == null)
                scheduler.schedule(new JobInfo.Builder(FOREGROUND, job).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
            else if (!usefulForeground) scheduler.cancel(FOREGROUND);
        } catch (Exception error) { android.util.Log.w("HongguoReserve", "预约后台检查调度未完成", error); }
    }
}
