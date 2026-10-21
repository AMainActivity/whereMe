package ru.ama.whereme.data.network

import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.ama.whereme.data.di.DataModule
import ru.ama.whereme.data.network.model.*
import ru.ama.whereme.domain.entity.LocationDb

class JsonContractTest {
    private val originalZone = java.util.TimeZone.getDefault()
    @org.junit.Before fun useUtc() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")) }
    @org.junit.After fun restoreZone() { java.util.TimeZone.setDefault(originalZone) }
    private val json = DataModule.provideJson()
    private val retrofit = Retrofit.Builder().baseUrl("https://example.test/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    private fun <T> response(type: Class<T>, raw: String): T? =
        raw.toResponseBody("application/json".toMediaType()).use {
            retrofit.responseBodyConverter<T>(type, emptyArray()).convert(it)
        }

    @Test fun retrofitReadsLoginResponseWithLegacyNamesAndAdditionalFields() {
        val body = response(JsonJwtDto::class.java, """{"error":false,"message":"1","tokenJWT":"test-token","posid":12,"famid":34,"isactivate":1,"name":"Имя","url":null,"extra":42}""")
        assertEquals(JsonJwtDto(false, "1", "test-token", 12, 34, "Имя", null, 1), body)
    }

    @Test fun rejectedLoginDoesNotRequireProfileFields() {
        assertEquals(JsonJwtDto(true, "invalid code"), response(JsonJwtDto::class.java,
            """{"error":true,"message":"invalid code"}"""))
    }

    @Test fun retrofitReadsUploadAcknowledgementAndServerRejection() {
        assertEquals(JsonDto(false, "1"), response(JsonDto::class.java, """{"error":false,"message":"1","extra":true}"""))
        assertEquals(JsonDto(true, "rejected"), response(JsonDto::class.java, """{"error":true,"message":"rejected"}"""))
    }

    @Test fun legacyNumericMessageIsReadAsString() {
        assertEquals(JsonDto(false, "1"), response(JsonDto::class.java,
            """{"error":false,"message":1}"""))
    }

    @Test fun malformedOrIncompleteAcknowledgementsCannotBecomeSuccess() {
        for (raw in listOf("{}", "null", "<html>error</html>", """{"error":false}""", """{"message":"1"}""")) {
            try {
                response(JsonDto::class.java, raw)
                fail("Invalid acknowledgement was accepted")
            } catch (_: SerializationException) { }
        }
    }

    @Test fun mapAndUploadKeepAllLegacyLocationFieldsAndOmitNulls() {
        val location = LocationDb(7, "1000", Instant.ofEpochMilli(1000), null, "Имя \"тест\"\n<&>",
            55.25, 37.5, 1, 5f, 0f, 0)
        val points = listOf(LocationDto.from(location))
        val map = json.encodeToString(points)
        val expected = Json.parseToJsonElement("""[{"_id":7,"datetime":"1000","datestart":"01.01.1970 00:00:01","info":"Имя \"тест\"\n<&>","latitude":55.25,"longitude":37.5,"sourceId":1,"accuracy":5.0,"velocity":0.0,"isWrite":0}]""")
        assertEquals(expected, Json.parseToJsonElement(map))
        val upload = Json.parseToJsonElement(json.encodeToString(DatasToJson("test-token", points))).jsonObject
        assertEquals(expected, upload.getValue("mdata"))
        assertEquals(setOf("tokenJWT", "mdata"), upload.keys)
        assertEquals("[]", json.encodeToString(emptyList<LocationDto>()))
    }

    @Test fun completedLocationKeepsEndTimeAndNullableInfoIsOmitted() {
        val point = LocationDto.from(LocationDb(8, "1000", Instant.ofEpochMilli(1000), Instant.ofEpochMilli(2000), null, 0.0, 0.0, 2, 50f, 0f, 1))
        val encoded = Json.parseToJsonElement(json.encodeToString(point)).jsonObject
        assertEquals("01.01.1970 00:00:02", encoded.getValue("dateend").jsonPrimitive.content)
        assertFalse(encoded.containsKey("info"))
    }
}
