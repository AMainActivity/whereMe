package ru.ama.whereme.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import ru.ama.whereme.domain.tracking.UploadResult

class LocationSyncWorker(context: Context, parameters: WorkerParameters,
    private val uploader: PendingLocationUploader) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
      try {
        // Every call uploads one bounded page. Stop/cancellation is cooperative between pages and in HTTP.
        while (true) {
            when (uploader.uploadPending()) {
                UploadResult.SKIPPED -> return Result.success()
                UploadResult.FAILED -> return Result.retry()
                UploadResult.SENT -> Unit
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (failure: Exception) {
        android.util.Log.w("LocationSync", "Storage unavailable: ${failure.javaClass.simpleName}")
        return Result.retry()
      }
    }
}
