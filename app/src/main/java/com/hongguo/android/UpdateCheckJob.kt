package com.hongguo.android

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UpdateCheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        task = scope.launch {
            try {
                val preferences = getSharedPreferences("app_updates", Context.MODE_PRIVATE)
                if (!BuildConfig.LOCAL_UPDATE_ENABLED || !preferences.getBoolean("automatic_checks", true)) return@launch
                val source = runCatching { UpdateSource.valueOf(preferences.getString("source", "AUTO").orEmpty()) }
                    .getOrDefault(UpdateSource.AUTO)
                val update = UpdateRepository().check(BuildConfig.UPDATE_MANIFEST_URL, BuildConfig.UPDATE_REPOSITORY, source)
                if (update.isNewerThan(BuildConfig.VERSION_CODE.toLong()) && update.minSdk <= Build.VERSION.SDK_INT &&
                    preferences.getBoolean("automatic_checks", true)) UpdateNotifications.post(applicationContext, update)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* 下次系统调度时重试；不弹网络错误。 */ }
            finally { if (isActive) jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
