package ru.ama.whereme.data.network.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody

data class JsonDto(
    @Expose
    @SerializedName("error") val error: Boolean,
    @Expose
    @SerializedName("message") val message: String
)

data class ResponseDto(
    val mBody: JsonDto? = null,
    val respIsSuccess: Boolean,
    val respError: ResponseBody? = null,
    val respCode: Int
)