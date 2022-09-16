package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import okhttp3.ResponseBody
import ru.ama.whereme.data.network.model.JsonJwtDto

@Parcelize
data class JsonJwt(
    val error: Boolean,
    val message: String,
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val isActivate: Int
) : Parcelable


data class ResponseJwtEntity(
    val mBody: JsonJwt?=null,
    val respIsSuccess: Boolean,
    val respError:ResponseBody?=null,
    val respCode:Int
)