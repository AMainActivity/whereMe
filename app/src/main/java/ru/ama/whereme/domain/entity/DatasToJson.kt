package ru.ama.whereme.domain.entity

data class DatasToJson(
    val tokenJWT: String,
    val mdata: List<LocationDb>
)