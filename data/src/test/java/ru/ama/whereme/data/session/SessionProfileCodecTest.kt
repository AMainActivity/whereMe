package ru.ama.whereme.data.session

import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

class SessionProfileCodecTest {
    private val legacy = """{"tokenJwt":"test-token","posId":12,"famId":34,"isActivate":true}"""
    private val profile = SettingsUserInfoDomModel("test-token", 12, 34, null, null, true)

    @Test fun readsExistingProfileWithOmittedOptionalFields() {
        assertEquals(profile, SessionProfileCodec.decode(legacy))
    }

    @Test fun readsExplicitNullsAndUnknownFields() {
        assertEquals(profile, SessionProfileCodec.decode(legacy.dropLast(1) +
            ""","name":null,"url":null,"futureField":1}"""))
    }

    @Test fun writesExistingFieldNamesAndOmitsNullsAndComputedOwner() {
        assertEquals(Json.parseToJsonElement(legacy), Json.parseToJsonElement(SessionProfileCodec.encode(profile)))
    }

    @Test fun roundTripsUnicodeEscapesAndInactiveProfile() {
        val value = profile.copy(tokenJwt = "token\"\\\n", name = "Имя <&> \"тест\"", url = "https://example.test/?a=1&b=2", isActivate = false)
        assertEquals(value, SessionProfileCodec.decode(SessionProfileCodec.encode(value)))
    }

    @Test fun incompleteOrCorruptProfilesAreRejectedWithoutTokenInTopLevelMessage() {
        for (raw in listOf("{}", "null", "[]", "broken", legacy.replace("true", "null"),
            legacy.replace("12", "12.5"), legacy.replace("12", "2147483648"),
            legacy.replace("\"test-token\"", "null"))) {
            try {
                SessionProfileCodec.decode(raw)
                fail("Corrupt profile was accepted")
            } catch (failure: IOException) {
                assertFalse(failure.message.orEmpty().contains("test-token"))
                assertNotNull(failure.cause)
            }
        }
    }
}
