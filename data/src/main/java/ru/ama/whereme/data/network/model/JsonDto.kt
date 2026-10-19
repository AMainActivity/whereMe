package ru.ama.whereme.data.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class JsonDto(
    @SerialName("error") val error: Boolean,
    @SerialName("message") val message: String
)