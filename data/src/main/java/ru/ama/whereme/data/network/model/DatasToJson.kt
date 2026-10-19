package ru.ama.whereme.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DatasToJson(
    val tokenJWT: String,
    val mdata: List<LocationDto>
)