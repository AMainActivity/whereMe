package ru.ama.whereme.data.database


data class SettingsUserInfoDataModel(
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val url: String? = null,
    val isActivate: Boolean
)