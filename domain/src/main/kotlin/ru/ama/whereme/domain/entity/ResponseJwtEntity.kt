package ru.ama.whereme.domain.entity


data class ResponseJwtEntity(
    val mBody: JsonJwt? = null,
    val respIsSuccess: Boolean,
    val respCode: Int
)