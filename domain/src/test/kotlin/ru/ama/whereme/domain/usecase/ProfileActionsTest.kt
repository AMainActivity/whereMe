package ru.ama.whereme.domain.usecase

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.*

class ProfileActionsTest {
    private val body = JsonJwt(false, "1", "token", 7, 8, "Name", "link", 1)
    private val profile = SettingsUserInfoDomModel("token", 7, 8, "Name", "link", true)
    private val expected = SessionSnapshot(profile, "first")
    private var response = ResponseJwtEntity(body, true, 200)
    private var logoutResponse = ResponseEntity(JsonEntity(false, "1"), true, 200)
    private var clearCalls = 0
    private var fail = false
    private val sessions =
        object : SessionRepository {
            override val session = MutableStateFlow(expected)

            override suspend fun signIn(profile: SettingsUserInfoDomModel) =
                error("Code check must not persist a profile")

            override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean {
                clearCalls++
                if (session.value != expected) return false
                session.value = SessionSnapshot(SettingsUserInfoDomModel.EMPTY, "signed-out")
                return true
            }

            override suspend fun updateActivation(
                expected: SessionSnapshot,
                active: Boolean,
            ): Boolean = error("Unexpected refresh")

            override suspend fun <T> withCurrent(
                expected: SessionSnapshot,
                action: suspend () -> T,
            ): T? = error("Unexpected ACK")
        }
    private val auth =
        object : AuthRepository {
            override suspend fun checkKod(code: String): ResponseJwtEntity {
                if (fail) throw IOException("offline")
                return response
            }

            override suspend fun checkWmJwToken(token: String): ResponseEntity =
                error("Unexpected refresh")

            override suspend fun logOut(token: String): ResponseEntity {
                assertEquals(expected.profile.tokenJwt, token)
                if (fail) throw IOException("offline")
                return logoutResponse
            }
        }

    @Test
    fun acceptedCodeReturnsDomainProfileWithoutSavingIt() = runTest {
        assertEquals(profile, CheckSignInCode(auth)("code"))
    }

    @Test
    fun explicitlyRejectedCodeIsNotAnAuthenticatedProfile() = runTest {
        response = response.copy(mBody = body.copy(message = "0"))
        assertNull(CheckSignInCode(auth)("code"))
    }

    @Test
    fun httpFailureIsNotReportedAsInvalidCode() = runTest {
        for (status in listOf(403, 429, 500, 503)) {
            response = ResponseJwtEntity(null, false, status)
            try {
                CheckSignInCode(auth)("code")
                fail("Expected request failure for HTTP $status")
            } catch (_: IOException) {}
        }
    }

    @Test
    fun malformedSuccessIsARequestFailure() = runTest {
        for (reply in
            listOf(
                response.copy(mBody = null),
                response.copy(mBody = body.copy(error = true)),
                response.copy(mBody = body.copy(message = "unexpected")),
                response.copy(mBody = body.copy(tokenJwt = "")),
            )) {
            response = reply
            try {
                CheckSignInCode(auth)("code")
                fail("Expected invalid response failure")
            } catch (_: IOException) {}
        }
    }

    @Test
    fun transportFailureIsNotReportedAsInvalidCode() = runTest {
        fail = true
        try {
            CheckSignInCode(auth)("code")
            fail("Expected network failure")
        } catch (_: IOException) {}
    }

    @Test
    fun successfulLogoutClearsOnlyTheExpectedSession() = runTest {
        assertTrue(SignOut(auth, sessions)(expected))
        assertEquals(1, clearCalls)
        assertTrue(sessions.session.value.profile.tokenJwt.isBlank())
    }

    @Test
    fun rejectedLogoutKeepsLocalCredentials() = runTest {
        logoutResponse = logoutResponse.copy(mBody = JsonEntity(false, "0"))
        assertFalse(SignOut(auth, sessions)(expected))
        assertEquals(0, clearCalls)
        assertEquals(expected, sessions.session.value)
    }

    @Test
    fun responseToOldLogoutCannotClearANewerLogin() = runTest {
        val newer = expected.copy(generation = "second")
        sessions.session.value = newer
        assertFalse(SignOut(auth, sessions)(expected))
        assertEquals(newer, sessions.session.value)
    }

    @Test
    fun offlineLogoutKeepsCredentials() = runTest {
        fail = true
        try {
            SignOut(auth, sessions)(expected)
            fail("Expected network failure")
        } catch (_: IOException) {}
        assertEquals(expected, sessions.session.value)
        assertEquals(0, clearCalls)
    }
}
