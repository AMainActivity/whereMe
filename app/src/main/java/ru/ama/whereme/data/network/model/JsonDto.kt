package ru.ama.whereme.data.network.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class JsonDto(
    @Expose
    @SerializedName("error") val error: Boolean,
    @Expose
    @SerializedName("message") val message: String
)