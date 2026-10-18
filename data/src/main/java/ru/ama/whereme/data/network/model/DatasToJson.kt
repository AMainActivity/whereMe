package ru.ama.whereme.data.network.model

import ru.ama.whereme.domain.entity.LocationDb

data class DatasToJson(
    val tokenJWT: String,
    val mdata: List<LocationDb>
)