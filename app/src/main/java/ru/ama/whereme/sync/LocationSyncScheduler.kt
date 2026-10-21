package ru.ama.whereme.sync

import android.app.Application
import androidx.work.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationSyncScheduler @Inject constructor(private val application: Application) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule() {
        val manager = WorkManager.getInstance(application)
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocationSyncWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS).setConstraints(constraints).build())
        manager.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocationSyncWorker>().setConstraints(constraints).build())
    }
    private companion object {
        const val PERIODIC_WORK_NAME = "location-sync-periodic"
        const val IMMEDIATE_WORK_NAME = "location-sync"
    }
}
