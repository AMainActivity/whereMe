package ru.ama.whereme.presentation

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import ru.ama.whereme.tracking.TrackingStartup

@HiltAndroidApp
class MyApp : Application() {
    @Inject lateinit var trackingStartup: TrackingStartup

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(trackingStartup)
    }
}
