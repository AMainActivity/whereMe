package ru.ama.whereme.domain.usecase

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample

@OptIn(ExperimentalCoroutinesApi::class)
class SelectLocationFixTest {
    private fun fix(accuracy: Float, time: Long = 1) =
        LocationOutcome.Fix(LocationSample(1.0, 2.0, accuracy, 0f, Instant.ofEpochMilli(time), time))

    @Test fun targetAccuracyEndsCollectionAndReleasesTheSource() = runTest {
        var released = false
        val result = selectLocationFix(flow {
            try { emit(fix(100f)); delay(1_000); emit(fix(50f)); awaitCancellation() }
            finally { released = true }
        }, 50, 15_000)
        assertEquals(fix(50f), result)
        assertEquals(1_000, testScheduler.currentTime)
        assertTrue(released)
    }

    @Test fun timeoutSelectsTheBestOfInterleavedProviderUpdates() = runTest {
        val result = selectLocationFix(flow {
            emit(fix(150f)); delay(1_000)
            emit(fix(80f)); delay(1_000)
            emit(fix(120f)); awaitCancellation()
        }, 50, 15_000)
        assertEquals(fix(80f), result)
        assertEquals(15_000, testScheduler.currentTime)
    }

    @Test fun equallyAccurateLaterUpdateWinsWithoutAnAgeLimit() = runTest {
        val result = selectLocationFix(flow {
            emit(fix(80f, 1)); delay(1_000); emit(fix(80f, 2)); awaitCancellation()
        }, 50, 40_000)
        assertEquals(fix(80f, 2), result)
    }

    @Test fun emptyWindowReturnsNoFix() = runTest {
        val result = selectLocationFix(flow { awaitCancellation() }, 50, 15_000)
        assertEquals(LocationOutcome.Unavailable(LocationOutcome.Reason.NO_FIX), result)
    }

    @Test fun externalCancellationDoesNotPublishTheAccumulatedFallback() = runTest {
        var released = false
        val result = async {
            selectLocationFix(flow {
                try { emit(fix(80f)); awaitCancellation() }
                finally { released = true }
            }, 50, 15_000)
        }
        runCurrent(); advanceTimeBy(1_000)
        result.cancel(); runCurrent()
        assertTrue(result.isCancelled)
        assertTrue(released)
    }
}
