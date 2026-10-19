package ru.ama.whereme.presentation

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import ru.ama.whereme.tracking.TrackingStartup

@HiltAndroidApp
class MyApp : Application(), androidx.work.Configuration.Provider {
    @Inject lateinit var workerFactory: ru.ama.whereme.sync.LocationWorkerFactory
    @Inject lateinit var sync: ru.ama.whereme.sync.LocationSyncScheduler
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().setWorkerFactory(workerFactory).build()
    @Inject lateinit var trackingStartup: TrackingStartup

    override fun onCreate() {
        super.onCreate()
        sync.schedule()
        ProcessLifecycleOwner.get().lifecycle.addObserver(trackingStartup)
    }
}
