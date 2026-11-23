package ru.topacademy.omni.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.topacademy.omni.data.repository.OmniRepository
import ru.topacademy.omni.widget.ScheduleGlanceWidget
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ScheduleSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = OmniRepository(applicationContext)
        if (!repository.isLoggedIn()) {
            return Result.success()
        }

        // Quiet nighttime hours (23:00 - 07:00): avoid waking up the radio
        val currentHour = LocalTime.now().hour
        if (currentHour in 23..23 || currentHour < 7) {
            try {
                ScheduleGlanceWidget().updateAll(applicationContext)
            } catch (_: Exception) {}
            return Result.success()
        }

        try {
            // Background sync: update schedule grid and tasks; skip heavy attendance queries
            repository.syncWeekSchedule(0, fetchAttendance = false)
            repository.syncTasks()
            // Re-render widget on home screen with fresh data
            ScheduleGlanceWidget().updateAll(applicationContext)
            return Result.success()
        } catch (_: Exception) {
            // Do not trigger aggressive retries on network errors to save battery
            return Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "omni_schedule_periodic_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // 2-hour interval saves battery and prevents waking the radio every 15 minutes
            val workRequest = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
                repeatInterval = 2,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            // Using UPDATE replaces any existing legacy 15-minute sync worker on the device
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
