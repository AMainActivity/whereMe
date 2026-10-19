package ru.ama.whereme.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import javax.inject.Inject
import javax.inject.Provider

class LocationWorkerFactory @Inject constructor(private val uploader: Provider<PendingLocationUploader>) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == LocationSyncWorker::class.java.name) LocationSyncWorker(appContext, workerParameters, uploader.get()) else null
}
