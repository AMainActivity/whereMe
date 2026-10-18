package ru.ama.whereme.data.sync

import com.google.gson.JsonParser
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.network.model.JsonDto
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.entity.SessionSnapshot

class LocationBatchUploaderTest {
    private val session = SessionSnapshot(SettingsUserInfoDomModel("test-token", 12, 34, "Test", null, true), "test-generation")
    private val row = LocationDbModel("1000", 1000, null, "sample", 1.5, 2.5, 1, 5f, 0f, 0).apply { _id = 7 }
    private val wire = LocationDb(7, "1000", "01.01.1970 00:00:01", null, "sample", 2.5, 4.5, 1, 5f, 0f, 0)
    private val batch = PendingLocationBatch(session, listOf(row), listOf(wire))

    @Test
    fun missingSessionOrDataMakesNoRequest() = runBlocking {
        val fixture = Fixture(null)
        fixture.uploader.uploadPending()
        assertEquals(0, fixture.requests)
        assertEquals(0, fixture.acks)
    }

    @Test
    fun emptyPayloadMakesNoRequest() = runBlocking {
        val fixture = Fixture(batch.copy(payload = emptyList()))
        fixture.uploader.uploadPending()
        assertEquals(0, fixture.requests)
    }

    @Test
    fun changedSessionBeforeRequestMakesNoRequest() = runBlocking {
        val fixture = Fixture(batch)
        fixture.current = false
        fixture.uploader.uploadPending()
        assertEquals(0, fixture.requests)
        assertEquals(0, fixture.acks)
    }

    @Test
    fun positiveResponseAcknowledgesExactSnapshot() = runBlocking {
        val fixture = Fixture(batch)
        fixture.uploader.uploadPending()
        assertEquals(1, fixture.requests)
        assertEquals(1, fixture.acks)
        assertTrue(fixture.acknowledged === batch)
    }

    @Test
    fun zeroUpdatedRowsDoesNotRetryAcknowledgement() = runBlocking {
        val fixture = Fixture(batch)
        fixture.ackResult = 0
        fixture.uploader.uploadPending()
        assertEquals(1, fixture.acks)
    }

    @Test
    fun legacyJsonNamesDatesAndTransformedCoordinatesArePreserved() = runBlocking {
        val fixture = Fixture(batch)
        fixture.uploader.uploadPending()
        val body = JsonParser.parseString(fixture.sent).asJsonObject
        assertEquals(setOf("tokenJWT", "mdata"), body.keySet())
        assertEquals(session.profile.tokenJwt, body["tokenJWT"].asString)
        val point = body["mdata"].asJsonArray.single().asJsonObject
        assertEquals(7L, point["_id"].asLong)
        assertEquals(wire.datestart, point["datestart"].asString)
        assertEquals(wire.latitude, point["latitude"].asDouble, 0.0)
        assertEquals(wire.longitude, point["longitude"].asDouble, 0.0)
    }

    @Test
    fun serverFailureLeavesSnapshotPending() = runBlocking {
        for (code in listOf(401, 403, 429, 500, 503)) {
            val fixture = Fixture(batch) { Response.error(code, "failure".toResponseBody()) }
            fixture.uploader.uploadPending()
            assertEquals(0, fixture.acks)
        }
    }

    @Test
    fun emptyOrNegativeBodyLeavesSnapshotPending() = runBlocking {
        for (body in listOf(null, JsonDto(true, "1"), JsonDto(false, ""))) {
            val fixture = Fixture(batch) { Response.success(body) }
            fixture.uploader.uploadPending()
            assertEquals(0, fixture.acks)
        }
    }

    @Test
    fun networkFailureLeavesSnapshotPending() = runBlocking {
        val fixture = Fixture(batch) { throw IOException("offline") }
        fixture.uploader.uploadPending()
        assertEquals(0, fixture.acks)
    }

    @Test
    fun malformedJsonLeavesSnapshotPending() = runBlocking {
        val fixture = Fixture(batch) { throw IllegalStateException("malformed JSON") }
        fixture.uploader.uploadPending()
        assertEquals(0, fixture.acks)
    }

    @Test
    fun sessionChangedWhileSendingDoesNotAcknowledge() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Response<JsonDto>>()
        val fixture = Fixture(batch) {
            started.complete(Unit)
            response.await()
        }
        val job = launch { fixture.uploader.uploadPending() }
        started.await()
        fixture.current = false
        response.complete(Response.success(JsonDto(false, "1")))
        job.join()
        assertEquals(0, fixture.acks)
    }

    @Test
    fun timeoutCancelsRequestAndKeepsPendingData() = runBlocking {
        var cancelled = false
        val fixture = Fixture(batch) {
            try { awaitCancellation() } finally { cancelled = true }
        }
        fixture.uploader.uploadPending(timeoutMillis = 50)
        assertTrue(cancelled)
        assertEquals(0, fixture.acks)
    }

    @Test
    fun stoppingCallerCancelsRequestWithoutAcknowledging() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var returned = false
        val fixture = Fixture(batch) {
            started.complete(Unit)
            awaitCancellation()
        }
        val job = launch {
            fixture.uploader.uploadPending()
            returned = true
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(returned)
        assertEquals(0, fixture.acks)
    }

    @Test
    fun concurrentRequestsAreSerialized() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val fixture = Fixture(batch) {
            active++
            maximum = maxOf(maximum, active)
            started.complete(Unit)
            release.await()
            active--
            Response.success(JsonDto(false, "1"))
        }
        val first = launch { fixture.uploader.uploadPending() }
        started.await()
        val second = launch { fixture.uploader.uploadPending() }
        yield()
        assertEquals(1, fixture.requests)
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, maximum)
        assertEquals(2, fixture.requests)
    }

    @Test
    fun cancelledQueuedRequestDoesNotStartAfterFirstCompletes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture(batch) {
            started.complete(Unit)
            release.await()
            Response.success(JsonDto(false, "1"))
        }
        val first = launch { fixture.uploader.uploadPending() }
        started.await()
        val second = launch { fixture.uploader.uploadPending() }
        yield()
        second.cancelAndJoin()
        release.complete(Unit)
        first.join()
        assertEquals(1, fixture.requests)
        assertEquals(1, fixture.acks)
    }

    @Test
    fun repositoryCancellationIsNotSwallowed() = runBlocking {
        val fixture = Fixture(batch) { throw CancellationException("stop") }
        var cancelled = false
        try { fixture.uploader.uploadPending() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(0, fixture.acks)
    }

    @Test
    fun failedDatabaseAcknowledgementPropagatesAndUnlocksUploader() = runBlocking {
        val fixture = Fixture(batch)
        fixture.ackFailure = true
        var failed = false
        try { fixture.uploader.uploadPending() } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        fixture.ackFailure = false
        fixture.uploader.uploadPending()
        assertEquals(2, fixture.requests)
        assertEquals(2, fixture.acks)
    }

    private class Fixture(
        private val batch: PendingLocationBatch?,
        private val reply: suspend () -> Response<JsonDto> = { Response.success(JsonDto(false, "1")) }
    ) : PendingLocationStore {
        var current = true
        var requests = 0
        var acks = 0
        var ackResult = 1
        var ackFailure = false
        var acknowledged: PendingLocationBatch? = null
        var sent = ""

        private val unused = Proxy.newProxyInstance(
            WmApiService::class.java.classLoader, arrayOf(WmApiService::class.java)
        ) { _, method, _ -> throw AssertionError("Unexpected API call: ${method.name}") } as WmApiService
        private val api = object : WmApiService by unused {
            override suspend fun writeLocDatas(request: RequestBody): Response<JsonDto> {
                requests++
                sent = Buffer().also(request::writeTo).readUtf8()
                return reply()
            }
        }
        val uploader = LocationBatchUploader(this, api)
        override suspend fun snapshot() = batch
        override suspend fun isCurrent(batch: PendingLocationBatch) = current
        override suspend fun acknowledge(batch: PendingLocationBatch): Int {
            acks++
            if (ackFailure) error("database unavailable")
            acknowledged = batch
            return ackResult
        }
    }
}
