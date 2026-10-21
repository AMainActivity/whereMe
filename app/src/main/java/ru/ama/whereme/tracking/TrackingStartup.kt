package ru.ama.whereme.tracking

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.domain.tracking.TrackingRequirement

/** Process foreground entry, not individual screens or Activity recreation, resumes the enabled schedule. */
@Singleton
class TrackingStartup @Inject constructor(
    private val application: Application,
    private val coordinator: TrackingCoordinator,
    private val scheduler: TrackingScheduler,
    @param:ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        // Enqueue before a subsequent user Stop; initialization/network must not postpone this intent.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val requirement = application.trackingCapabilities().requirement()
            if (requirement != TrackingRequirement.READY) {
                Log.d(TAG, "Automatic start unavailable: $requirement")
                return@launch
            }
            try {
                coordinator.resumeEnabledSchedule()
                scheduler.scheduleNextAlarmClockIfEnabled()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to reconcile tracking: " + failure.javaClass.simpleName)
            }
        }
    }
    private companion object {
        const val TAG = "TrackingStartup"
    }
}
