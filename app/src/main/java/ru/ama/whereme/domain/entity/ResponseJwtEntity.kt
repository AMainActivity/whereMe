package ru.ama.whereme.domain.entity

import okhttp3.ResponseBody

data class ResponseJwtEntity(
    val mBody: JsonJwt? = null,
    val respIsSuccess: Boolean,
    val respError: ResponseBody? = null,
    val respCode: Int
)