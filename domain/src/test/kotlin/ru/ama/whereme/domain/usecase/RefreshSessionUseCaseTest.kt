package ru.ama.whereme.domain.usecase

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.SessionSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ama.whereme.domain.entity.JsonEntity
import ru.ama.whereme.domain.entity.ResponseEntity
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.AuthRepository
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.entity.ResponseJwtEntity

class RefreshSessionUseCaseTest {
    private val initial = SessionSnapshot(SettingsUserInfoDomModel("test-token", 12, 34, "Test", null, true), "initial")

    @Test
    fun transportFailurePreservesCredentials() = runBlocking {
        val fixture = Fixture(initial) { throw IOException("offline") }
        fixture.refresh(initial)
        assertEquals(initial, fixture.session)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun httpFailuresIncludingUnauthorizedDoNotInventLogoutPolicy() = runBlocking {
        for (code in listOf(401, 403, 429, 500, 503)) {
            val fixture = Fixture(initial) { ResponseEntity(respIsSuccess = false, respCode = code) }
            fixture.refresh(initial)
            assertEquals(initial, fixture.session)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun ambiguousBodiesPreserveCredentials() = runBlocking {
        for (body in listOf(null, JsonEntity(true, "0"), JsonEntity(false, ""), JsonEntity(false, "unknown"))) {
            val fixture = Fixture(initial) { ResponseEntity(body, true, respCode = 200) }
            fixture.refresh(initial)
            assertEquals(initial, fixture.session)
            assertEquals(0, fixture.writes)
        }
    }

    @Test
    fun malformedResponsePreservesCredentials() = runBlocking {
        val fixture = Fixture(initial) { throw IllegalStateException("invalid server JSON") }
        fixture.refresh(initial)
        assertEquals(initial, fixture.session)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun confirmedInactiveStatusKeepsTokenAndAccount() = runBlocking {
        val fixture = Fixture(initial) { response("0") }
        fixture.refresh(initial)
        assertEquals(initial.copy(isActivate = false), fixture.session)
        assertEquals(1, fixture.writes)
    }

    @Test
    fun confirmedActiveStatusUpdatesInactiveSession() = runBlocking {
        val inactive = initial.copy(isActivate = false)
        val fixture = Fixture(inactive) { response("1") }
        fixture.refresh(inactive)
        assertEquals(initial, fixture.session)
        assertEquals(1, fixture.writes)
    }

    @Test
    fun unchangedStatusDoesNotRewriteSettings() = runBlocking {
        val fixture = Fixture(initial) { response("1") }
        fixture.refresh(initial)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun blankTokenDoesNotMakeRequest() = runBlocking {
        val empty = initial.copy(tokenJwt = " ")
        val fixture = Fixture(empty) { error("request must not happen") }
        fixture.refresh(empty)
        assertEquals(0, fixture.requests)
    }

    @Test
    fun sessionChangedBeforeRequestIsIgnored() = runBlocking {
        val current = initial.copy(tokenJwt = "new-test-token")
        val fixture = Fixture(current) { error("request must not happen") }
        fixture.refresh(initial)
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
            val job = launch { fixture.refresh(initial) }
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
        fixture.refresh(initial, timeoutMillis = 50)
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
            fixture.refresh(initial)
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
            fixture.refresh(initial)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun sameCredentialsAfterReloginStillRejectOldResponse() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<ResponseEntity>()
        val fixture = Fixture(initial) { started.complete(Unit); reply.await() }
        val job = launch { fixture.refresh(initial) }
        started.await()
        fixture.session = initial.copy(generation = "new-login")
        reply.complete(response("0"))
        job.join()
        assertEquals("new-login", fixture.session.generation)
        assertTrue(fixture.session.profile.isActivate)
        assertEquals(0, fixture.writes)
    }

    private fun SessionSnapshot.copy(tokenJwt: String = profile.tokenJwt,
        posId: Int = profile.posId, isActivate: Boolean = profile.isActivate) =
        copy(profile = profile.copy(tokenJwt = tokenJwt, posId = posId, isActivate = isActivate))

    private fun response(message: String) = ResponseEntity(JsonEntity(false, message), true, respCode = 200)

    private class Fixture(initial: SessionSnapshot, val reply: suspend () -> ResponseEntity) {
        var session = initial
        var writes = 0
        var requests = 0

        private val repository = object : SessionRepository, AuthRepository {
            override val session get() = flowOf(this@Fixture.session)
            override suspend fun signIn(profile: SettingsUserInfoDomModel) { error("Unexpected signIn") }
            override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean = error("Unexpected logout")
            override suspend fun updateActivation(expected: SessionSnapshot, active: Boolean): Boolean {
                if (this@Fixture.session != expected) return false
                if (expected.profile.isActivate != active) {
                    this@Fixture.session = expected.copy(profile = expected.profile.copy(isActivate = active))
                    writes++
                }
                return true
            }
            override suspend fun <T> withCurrent(expected: SessionSnapshot, action: suspend () -> T): T? = error("Unexpected lease")
            override suspend fun checkKod(code: String): ResponseJwtEntity = error("Unexpected login")
            override suspend fun logOut(token: String): ResponseEntity = error("Unexpected logout")
            override suspend fun checkWmJwToken(token: String): ResponseEntity {
                requests++
                return reply()
            }
        }
        val refresh = RefreshSessionUseCase(repository, repository)
    }
}
