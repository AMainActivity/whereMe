package ru.ama.whereme.tracking

import java.time.Instant
import java.util.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.network.model.LocationDto
import ru.ama.whereme.feature.map.MapPoint

class LocationTimeContractTest {
    @Test
    fun persistedMillisKeepTheirMeaningAndBothWireFormatsAcrossTimeZones() {
        val originalZone = TimeZone.getDefault()
        try {
            val stored = LocationDbModel("1234", 1234, 2345, "interval", 1.0, 2.0, 1, 5f, 0f, 0)
            val location = WmMapper().mapDbModelsToEntities(listOf(stored)).single()
            assertEquals("1234", location.datetime)
            assertEquals(Instant.ofEpochMilli(1234), location.datestart)
            assertEquals(Instant.ofEpochMilli(2345), location.dateend)
            val json = Json { explicitNulls = false }
            for ((zone, hour) in listOf("UTC" to "00", "Asia/Yekaterinburg" to "05")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val upload = json.parseToJsonElement(json.encodeToString(LocationDto.from(location)))
                val map = json.parseToJsonElement(json.encodeToString(MapPoint.from(location)))
                assertEquals(upload, map)
                val fields = upload.jsonObject
                assertEquals("1234", fields.getValue("datetime").jsonPrimitive.content)
                assertEquals("01.01.1970 $hour:00:01", fields.getValue("datestart").jsonPrimitive.content)
                assertEquals("01.01.1970 $hour:00:02", fields.getValue("dateend").jsonPrimitive.content)
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
    @Test
    fun datetimeIsPassedThroughWithoutParsingOrNormalizingItsText() {
        val stored = LocationDbModel("0001234", 1234, null, null, 1.0, 2.0, 1, 5f, 0f, 0)
        val location = WmMapper().mapDbModelsToEntities(listOf(stored)).single()
        assertEquals(stored.datetime, location.datetime)
        assertEquals(stored.datetime, LocationDto.from(location).datetime)
        assertEquals(stored.datetime, MapPoint.from(location).datetime)
    }

}
