package ru.ama.whereme.data.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Rejected login responses can omit the profile fields. */
@Serializable
data class JsonJwtDto(
    @SerialName("error") val error: Boolean,
    @SerialName("message") val message: String,
    @SerialName("tokenJWT") val tokenJwt: String = "",
    @SerialName("posid") val posId: Int = 0,
    @SerialName("famid") val famId: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("isactivate") val isActivate: Int = 0
)