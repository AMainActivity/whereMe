package ru.ama.whereme.tracking

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val RECEIVER_TIMEOUT_MILLIS = 8_000L
private const val TAG = "TrackingReceiver"

/** The receiver owns a bounded async result while DataStore initializes off Main. */
fun BroadcastReceiver.runTrackingWork(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try { withTimeout(RECEIVER_TIMEOUT_MILLIS) { block() }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { Log.w(TAG, "Unavailable: " + failure.javaClass.simpleName)
        } finally { pending.finish() }
    }
}
