package io.github.jackotom.hongguo.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

public final class ReservationJob extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<Integer, Thread> running = new java.util.HashMap<>();

    @Override public boolean onStartJob(JobParameters params) {
        int id = params.getJobId();
        Thread thread = new Thread(() -> {
            try { ReservationPoller.check(getApplicationContext()); } catch (Exception ignored) { }
            Thread finished = Thread.currentThread();
            main.post(() -> {
                if (running.get(id) != finished) return;
                running.remove(id);
                jobFinished(params, false);
                ReservationSchedule.plan(this, false);
            });
        }, "reservation-check");
        running.put(id, thread);
        thread.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Thread thread = running.remove(params.getJobId());
        if (thread != null) thread.interrupt();
        return true;
    }

    @Override public void onDestroy() {
        for (Thread thread : running.values()) thread.interrupt();
        running.clear();
        super.onDestroy();
    }
}
