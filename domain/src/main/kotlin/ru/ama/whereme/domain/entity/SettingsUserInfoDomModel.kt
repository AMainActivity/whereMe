package ru.ama.whereme.domain.entity



data class SettingsUserInfoDomModel(
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val url: String? = null,
    val isActivate: Boolean
)