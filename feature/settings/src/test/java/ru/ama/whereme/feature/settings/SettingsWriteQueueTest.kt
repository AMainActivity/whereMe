package ru.ama.whereme.feature.settings

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWriteQueueTest {
    @Test
    fun closingDrainsAcceptedCommandsInOrder() = runTest {
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<Int>()
        val writer = SettingsWriteQueue(backgroundScope) { error("Unexpected failure") }
        writer.submit {
            release.await()
            saved.add(1)
        }
        writer.submit { saved.add(2) }
        runCurrent()
        writer.close()
        assertTrue(saved.isEmpty())
        release.complete(Unit)
        writer.completion.join()
        assertEquals(listOf(1, 2), saved)
    }

    @Test
    fun failedWriteDoesNotDiscardNextAcceptedEdit() = runTest {
        var failures = 0
        var saved = false
        val writer = SettingsWriteQueue(backgroundScope) { failures++ }
        writer.submit { throw IOException("injected failure") }
        writer.submit { saved = true }
        writer.close()
        writer.completion.join()
        assertEquals(1, failures)
        assertTrue(saved)
    }

    @Test
    fun cancellationIsNotConvertedIntoSaveFailure() = runTest {
        var failures = 0
        var completed = false
        val writer = SettingsWriteQueue(backgroundScope) { failures++ }
        writer.submit {
            try {
                awaitCancellation()
            } finally {
                completed = true
            }
        }
        runCurrent()
        backgroundScope.cancel()
        writer.completion.join()
        assertTrue(writer.completion.isCancelled)
        assertTrue(completed)
        assertEquals(0, failures)
    }
}
