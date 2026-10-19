package ru.ama.whereme.sync

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
class LocationSyncWorkerTest {
    private fun worker(uploader: PendingLocationUploader) = TestListenableWorkerBuilder<LocationSyncWorker>(
        RuntimeEnvironment.getApplication()).setWorkerFactory(LocationWorkerFactory(Provider { uploader })).build()

    @Test fun backlogDrainsWithoutARunningTrackingService() = runBlocking {
        var pages = 0
        val worker = worker(PendingLocationUploader { if (++pages <= 3) UploadResult.SENT else UploadResult.SKIPPED })
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(4, pages)
    }
    @Test fun offlineFailureRequestsRetryAndStopsTheCurrentPass() = runBlocking {
        var calls = 0
        val worker = worker(PendingLocationUploader { calls++; UploadResult.FAILED })
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        assertEquals(1, calls)
    }
    @Test fun noProfileOrPendingRowsFinishesWithoutRetry() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(PendingLocationUploader { UploadResult.SKIPPED }).doWork())
    }
    @Test fun cancellationDoesNotBecomeSuccessOrRetry() = runBlocking {
        try {
            worker(PendingLocationUploader { throw CancellationException("cancel") }).doWork()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
    @Test fun databaseFailureRetriesLater() = runBlocking {
        assertEquals(ListenableWorker.Result.retry(), worker(PendingLocationUploader { throw java.io.IOException("unavailable") }).doWork())
    }
}
