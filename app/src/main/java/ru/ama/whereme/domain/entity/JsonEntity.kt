package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import okhttp3.ResponseBody

@Parcelize
data class JsonEntity(
    val error: Boolean,
    val message: String
) : Parcelable
data class ResponseEntity(
    val mBody: JsonEntity?=null,
    val respIsSuccess: Boolean,
    val respError: ResponseBody?=null,
    val respCode:Int
)