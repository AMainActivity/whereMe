package ru.ama.whereme.domain.usecase

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ama.whereme.domain.entity.JsonEntity
import ru.ama.whereme.domain.entity.ResponseEntity
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.WmRepository

class RefreshSessionUseCaseTest {
    private val request = "{}".toRequestBody()
    private val initial = SettingsUserInfoDomModel("test-token", 12, 34, "Test", null, true)

    @Test
    fun transportFailurePreservesCredentials() = runBlocking {
        val fixture = Fixture(initial) { throw IOException("offline") }
        fixture.refresh(initial, request)
        assertEquals(initial, fixture.session)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun httpFailuresIncludingUnauthorizedDoNotInventLogoutPolicy() = runBlocking {
        for (code in listOf(401, 403, 429, 500, 503)) {
            val fixture = Fixture(initial) { ResponseEntity(respIsSuccess = false, respCode = code) }
            fixture.refresh(initial, request)
            assertEquals(initial, fixture.session)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun ambiguousBodiesPreserveCredentials() = runBlocking {
        for (body in listOf(null, JsonEntity(true, "0"), JsonEntity(false, ""), JsonEntity(false, "unknown"))) {
            val fixture = Fixture(initial) { ResponseEntity(body, true, respCode = 200) }
            fixture.refresh(initial, request)
            assertEquals(initial, fixture.session)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun malformedResponsePreservesCredentials() = runBlocking {
        val fixture = Fixture(initial) { throw IllegalStateException("invalid server JSON") }
        fixture.refresh(initial, request)
        assertEquals(initial, fixture.session)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun confirmedInactiveStatusKeepsTokenAndAccount() = runBlocking {
        val fixture = Fixture(initial) { response("0") }
        fixture.refresh(initial, request)
        assertEquals(initial.copy(isActivate = false), fixture.session)
        assertEquals(1, fixture.writes)
    }

    @Test
    fun confirmedActiveStatusUpdatesInactiveSession() = runBlocking {
        val inactive = initial.copy(isActivate = false)
        val fixture = Fixture(inactive) { response("1") }
        fixture.refresh(inactive, request)
        assertEquals(initial, fixture.session)
        assertEquals(1, fixture.writes)
    }

    @Test
    fun unchangedStatusDoesNotRewriteSettings() = runBlocking {
        val fixture = Fixture(initial) { response("1") }
        fixture.refresh(initial, request)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun blankTokenDoesNotMakeRequest() = runBlocking {
        val empty = initial.copy(tokenJwt = " ")
        val fixture = Fixture(empty) { error("request must not happen") }
        fixture.refresh(empty, request)
        assertEquals(0, fixture.requests)
    }

    @Test
    fun sessionChangedBeforeRequestIsIgnored() = runBlocking {
        val current = initial.copy(tokenJwt = "new-test-token")
        val fixture = Fixture(current) { error("request must not happen") }
        fixture.refresh(initial, request)
        assertEquals(0, fixture.requests)
        assertEquals(current, fixture.session)
    }

    @Test
    fun lateResponseDoesNotOverwriteNewLoginOrLogout() = runBlocking {
        for (current in listOf(
            initial.copy(tokenJwt = "new-test-token", posId = 99),
            initial.copy(tokenJwt = "", isActivate = false)
        )) {
            val started = CompletableDeferred<Unit>()
            val reply = CompletableDeferred<ResponseEntity>()
            val fixture = Fixture(initial) {
                started.complete(Unit)
                reply.await()
            }
            val job = launch { fixture.refresh(initial, request) }
            started.await()
            fixture.session = current
            reply.complete(response("0"))
            job.join()
            assertEquals(current, fixture.session)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun timeoutCancelsRequestWithoutChangingSession() = runBlocking {
        var requestCancelled = false
        val fixture = Fixture(initial) {
            try {
                awaitCancellation()
            } finally {
                requestCancelled = true
            }
        }
        fixture.refresh(initial, request, timeoutMillis = 50)
        assertTrue(requestCancelled)
        assertEquals(initial, fixture.session)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun callerCancellationIsNotSwallowed() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var returnedNormally = false
        val fixture = Fixture(initial) {
            started.complete(Unit)
            awaitCancellation()
        }
        val job = launch {
            fixture.refresh(initial, request)
            returnedNormally = true
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(returnedNormally)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun repositoryCancellationPropagates() = runBlocking {
        val fixture = Fixture(initial) { throw CancellationException("cancelled") }
        var cancelled = false
        try {
            fixture.refresh(initial, request)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(0, fixture.writes)
    }

    private fun response(message: String) = ResponseEntity(JsonEntity(false, message), true, respCode = 200)

    private class Fixture(initial: SettingsUserInfoDomModel, val reply: suspend () -> ResponseEntity) {
        var session = initial
        var writes = 0
        var requests = 0

        // The legacy repository also contains location/alarm APIs. Fail on any unexpected use.
        private val unused = Proxy.newProxyInstance(
            WmRepository::class.java.classLoader,
            arrayOf(WmRepository::class.java)
        ) { _, method, _ -> throw AssertionError("Unexpected repository call: ${method.name}") } as WmRepository

        private val repository = object : WmRepository by unused {
            override fun getWmUserInfoSetings() = session
            override fun setWmUserInfoSetings(set: SettingsUserInfoDomModel) {
                session = set
                writes++
            }
            override suspend fun checkWmJwToken(request: RequestBody): ResponseEntity {
                requests++
                return reply()
            }
        }
        val refresh = RefreshSessionUseCase(repository)
    }
}
