package ru.ama.whereme.data.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.ama.whereme.data.di.DataModule
import ru.ama.whereme.data.network.model.*
import ru.ama.whereme.domain.entity.LocationDb

/** Executes the real Retrofit request converter; the interceptor prevents any network access. */
class RequestContractTest {
    private val sent = mutableListOf<JsonObject>()
    private val api = Retrofit.Builder()
        .baseUrl("https://example.test/")
        .addConverterFactory(DataModule.provideJson().asConverterFactory("application/json".toMediaType()))
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            val body = requireNotNull(request.body)
            assertEquals("application", body.contentType()?.type)
            assertEquals("json", body.contentType()?.subtype)
            sent.add(Json.parseToJsonElement(Buffer().also(body::writeTo).readUtf8()).jsonObject)
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"error":false,"message":"1"}""".toResponseBody("application/json".toMediaType())).build()
        }.build())
        .build().create(WmApiService::class.java)

    @Test fun allCredentialEndpointsKeepTheLegacyFieldAndEscapeTheValue() = runBlocking {
        val value = "код\"\\\n<&>"
        api.chekcKod(CredentialRequest(value))
        api.checkToken(CredentialRequest(value))
        api.logOut(CredentialRequest(value))
        assertEquals(3, sent.size)
        sent.forEach {
            assertEquals(setOf("kod"), it.keys)
            assertEquals(value, it.getValue("kod").jsonPrimitive.content)
        }
    }

    @Test fun uploadKeepsTheTokenAndLocationContractThroughTheRetrofitConverter() = runBlocking {
        val point = LocationDto.from(LocationDb(7, "1000", "start", "end", null, 55.25, 37.5, 1, 5f, 0f, 0))
        api.writeLocDatas(DatasToJson("test-token", listOf(point)))
        val request = sent.single()
        assertEquals(setOf("tokenJWT", "mdata"), request.keys)
        assertEquals("test-token", request.getValue("tokenJWT").jsonPrimitive.content)
        val expected = Json.parseToJsonElement("""[{"_id":7,"datetime":"1000","datestart":"start","dateend":"end","latitude":55.25,"longitude":37.5,"sourceId":1,"accuracy":5.0,"velocity":0.0,"isWrite":0}]""")
        assertEquals(expected, request.getValue("mdata"))
    }
}
