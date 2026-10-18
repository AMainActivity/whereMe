package ru.ama.whereme.domain.entity



data class SettingsUserInfoDomModel(
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val url: String? = null,
    val isActivate: Boolean
) {
    companion object {
        val EMPTY = SettingsUserInfoDomModel("", 0, 0, null, null, false)
    }
}
