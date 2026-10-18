package ru.ama.whereme.data.repository

import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import ru.ama.whereme.data.mapper.WmMapperJwt
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.data.network.model.JsonDto
import ru.ama.whereme.data.network.model.JsonJwtDto

class RemoteAuthRepositoryTest {
    @Test
    fun loginPreservesLegacyFieldAndEscapesCode() = runBlocking {
        val api = FakeApi()
        val code = "код\"\\\n<&>"
        val result = RemoteAuthRepository(api, WmMapperJwt()).checkKod(code)
        assertEquals("login", api.operation)
        assertCredential(code, api.sent)
        assertTrue(result.respIsSuccess)
        assertEquals("test-token", result.mBody?.tokenJwt)
    }

    @Test
    fun validationAndLogoutUseTokenInLegacyKodField() = runBlocking {
        val api = FakeApi()
        val repository = RemoteAuthRepository(api, WmMapperJwt())
        assertEquals("1", repository.checkWmJwToken("test-token").mBody?.message)
        assertEquals("validate", api.operation)
        assertCredential("test-token", api.sent)
        assertEquals("1", repository.logOut("test-token").mBody?.message)
        assertEquals("logout", api.operation)
        assertCredential("test-token", api.sent)
    }

    @Test
    fun errorBodiesAreClosedInsideDataForAllOperations() = runBlocking {
        val api = FakeApi()
        val repository = RemoteAuthRepository(api, WmMapperJwt())
        for (operation in listOf("login", "validate", "logout")) {
            val error = TrackedBody()
            api.error = error
            val code = when (operation) {
                "login" -> repository.checkKod("code").also { assertFalse(it.respIsSuccess) }.respCode
                "validate" -> repository.checkWmJwToken("token").also { assertFalse(it.respIsSuccess) }.respCode
                else -> repository.logOut("token").also { assertFalse(it.respIsSuccess) }.respCode
            }
            assertEquals(401, code)
            assertTrue(error.closed)
        }
    }

    @Test
    fun emptySuccessfulBodyIsNotInvented() = runBlocking {
        val api = FakeApi().apply { emptyBody = true }
        val result = RemoteAuthRepository(api, WmMapperJwt()).checkWmJwToken("token")
        assertTrue(result.respIsSuccess)
        assertEquals(null, result.mBody)
    }

    @Test
    fun cancellationRemainsOwnedByCaller() = runBlocking {
        val failure = CancellationException("cancelled")
        val api = FakeApi().apply { this.failure = failure }
        try {
            RemoteAuthRepository(api, WmMapperJwt()).logOut("token")
            error("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertTrue(actual === failure)
        }
    }

    @Test
    fun transportFailureDoesNotBecomeSuccessfulResponse() = runBlocking {
        val failure = IOException("offline")
        val api = FakeApi().apply { this.failure = failure }
        try {
            RemoteAuthRepository(api, WmMapperJwt()).checkKod("code")
            error("IOException must propagate to the use case")
        } catch (actual: IOException) {
            assertTrue(actual === failure)
        }
    }

    private fun assertCredential(value: String, sent: String) {
        val json = JsonParser.parseString(sent).asJsonObject
        assertEquals(setOf("kod"), json.keySet())
        assertEquals(value, json["kod"].asString)
    }

    private class TrackedBody : ResponseBody() {
        var closed = false
        private val tracked = object : ForwardingSource(Buffer().writeUtf8("rejected")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = tracked
    }

    private class FakeApi : WmApiService {
        var operation = ""
        var sent = ""
        var error: ResponseBody? = null
        var failure: Exception? = null
        var emptyBody = false

        private fun capture(operation: String, request: RequestBody) {
            this.operation = operation
            sent = Buffer().also(request::writeTo).readUtf8()
            failure?.let { throw it }
        }

        override suspend fun chekcKod(request: RequestBody): Response<JsonJwtDto> {
            capture("login", request)
            error?.let { return Response.error(401, it) }
            return Response.success(JsonJwtDto(false, "1", "test-token", 12, 34, "Test", null, 1))
        }

        override suspend fun checkToken(request: RequestBody): Response<JsonDto> {
            capture("validate", request)
            error?.let { return Response.error(401, it) }
            return Response.success(if (emptyBody) null else JsonDto(false, "1"))
        }

        override suspend fun logOut(request: RequestBody): Response<JsonDto> {
            capture("logout", request)
            error?.let { return Response.error(401, it) }
            return Response.success(JsonDto(false, "1"))
        }

        override suspend fun writeLocDatas(request: RequestBody): Response<JsonDto> = error("Unexpected upload")
    }
}
