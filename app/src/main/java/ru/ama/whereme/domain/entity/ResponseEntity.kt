package ru.ama.whereme.domain.entity

import okhttp3.ResponseBody

data class ResponseEntity(
    val mBody: JsonEntity? = null,
    val respIsSuccess: Boolean,
    val respError: ResponseBody? = null,
    val respCode: Int
)