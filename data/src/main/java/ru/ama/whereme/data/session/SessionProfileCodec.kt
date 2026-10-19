package ru.ama.whereme.data.session

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

/** Version-one profile JSON; field names are persisted in existing encrypted sessions. */
internal object SessionProfileCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(profile: SettingsUserInfoDomModel): String = json.encodeToString(
        StoredProfile(profile.tokenJwt, profile.posId, profile.famId, profile.name, profile.url, profile.isActivate)
    )

    fun decode(raw: String): SettingsUserInfoDomModel = try {
        val profile = json.decodeFromString<StoredProfile>(raw)
        SettingsUserInfoDomModel(profile.tokenJwt, profile.posId, profile.famId,
            profile.name, profile.url, profile.isActivate)
    } catch (failure: SerializationException) {
        // Do not include raw JSON or tokens in diagnostics shown to the user.
        throw IOException("Cannot decode session; stored data retained", failure)
    }

    @Serializable
    private data class StoredProfile(
        val tokenJwt: String,
        val posId: Int,
        val famId: Int,
        val name: String? = null,
        val url: String? = null,
        val isActivate: Boolean
    )
}
