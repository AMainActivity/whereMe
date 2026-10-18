package ru.ama.whereme.data.network.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class JsonJwtDto(
    @Expose
    @SerializedName("error") val error: Boolean,
    @Expose
    @SerializedName("message") val message: String,
    @Expose
    @SerializedName("tokenJWT") val tokenJwt: String,
    @Expose
    @SerializedName("posid") val posId: Int,
    @Expose
    @SerializedName("famid") val famId: Int,
    @Expose
    @SerializedName("name") val name: String? = null,
    @Expose
    @SerializedName("url") val url: String? = null,
    @Expose
    @SerializedName("isactivate") val isActivate: Int
)