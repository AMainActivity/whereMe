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
        manager.enqueueUniquePeriodicWork("location-sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocationSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        manager.enqueueUniqueWork("location-sync", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocationSyncWorker>().setConstraints(constraints).build())
    }
}
