package ru.ama.whereme.sync

import android.app.Application
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import ru.ama.whereme.domain.tracking.UploadResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LocationSyncSchedulerTest {
    @Test fun pendingWorkWaitsForNetworkAndRepeatedSchedulingKeepsOneRequest() = runBlocking {
        val application = RuntimeEnvironment.getApplication()
        var uploads = 0
        val factory = LocationWorkerFactory(Provider { PendingLocationUploader { uploads++; UploadResult.SKIPPED } })
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        try {
            val scheduler = LocationSyncScheduler(application)
            scheduler.schedule()
            scheduler.schedule()
            val manager = WorkManager.getInstance(application)
            val once = manager.getWorkInfosForUniqueWork("location-sync").get().single()
            val periodic = manager.getWorkInfosForUniqueWork("location-sync-periodic").get().single()
            assertEquals(NetworkType.CONNECTED, once.constraints.requiredNetworkType)
            assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
            assertEquals(WorkInfo.State.ENQUEUED, once.state)
            assertEquals(0, uploads)
            requireNotNull(WorkManagerTestInitHelper.getTestDriver(application)).setAllConstraintsMet(once.id)
            withTimeout(5_000) { manager.getWorkInfoByIdFlow(once.id).first { it?.state == WorkInfo.State.SUCCEEDED } }
            assertEquals(1, uploads)
        } finally { WorkManagerTestInitHelper.closeWorkDatabase() }
    }
}
