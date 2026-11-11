package com.hongguo.android

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

internal object UpdateSchedule {
    const val JOB_ID = 2308

    fun sync(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled || !BuildConfig.LOCAL_UPDATE_ENABLED) {
            scheduler.cancel(JOB_ID)
            context.getSystemService(NotificationManager::class.java).cancel(UpdateNotifications.NOTIFICATION_ID)
            return
        }
        val interval = TimeUnit.HOURS.toMillis(4)
        val existing = scheduler.getPendingJob(JOB_ID)
        if (existing != null && existing.isPersisted && existing.intervalMillis == interval) return
        // 系统可能因省电延迟周期任务，不承诺精确触发时间。
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(interval).setPersisted(true).build()
        scheduler.schedule(job)
    }
}
