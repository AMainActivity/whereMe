package ru.ama.whereme.tracking

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** The receiver owns a bounded async result while DataStore initializes off Main. */
fun BroadcastReceiver.runTrackingWork(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try { withTimeout(8_000) { block() }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { Log.w("TrackingReceiver", "Unavailable: " + failure.javaClass.simpleName)
        } finally { pending.finish() }
    }
}
