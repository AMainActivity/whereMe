package ru.ama.whereme.data.network.model

import okhttp3.ResponseBody

data class ResponseDto(
    val mBody: JsonDto? = null,
    val respIsSuccess: Boolean,
    val respError: ResponseBody? = null,
    val respCode: Int
)