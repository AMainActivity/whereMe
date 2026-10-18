package ru.ama.whereme.presentation

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SettingsWriteQueueTest {
    @Test fun closingDrainsAcceptedCommandsInOrder() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val saved = mutableListOf<Int>()
                val writer = SettingsWriteQueue(scope) { error("Unexpected failure") }
                writer.submit { started.complete(Unit); release.await(); saved.add(1) }
                writer.submit { saved.add(2) }
                started.await()
                writer.close()
                release.complete(Unit)
                writer.completion.join()
                assertEquals(listOf(1, 2), saved)
            } finally { scope.cancel() }
        }
    }
    @Test fun failedWriteDoesNotDiscardNextAcceptedEdit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var failures = 0
            var saved = false
            val writer = SettingsWriteQueue(scope) { failures++ }
            writer.submit { throw IOException("injected failure") }
            writer.submit { saved = true }
            writer.close()
            withTimeout(5_000) { writer.completion.join() }
            assertEquals(1, failures)
            assertTrue(saved)
        } finally { scope.cancel() }
    }
    @Test fun cancellationIsNotConvertedIntoSaveFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var failures = 0
            val started = CompletableDeferred<Unit>()
            val writer = SettingsWriteQueue(scope) { failures++ }
            writer.submit { started.complete(Unit); awaitCancellation() }
            started.await()
            scope.cancel()
            withTimeout(5_000) { writer.completion.join() }
            assertTrue(writer.completion.isCancelled)
            assertEquals(0, failures)
        } finally { scope.cancel() }
    }
}
