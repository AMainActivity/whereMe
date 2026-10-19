package ru.ama.whereme.domain.entity



data class SettingsUserInfoDomModel(
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val url: String? = null,
    val isActivate: Boolean
) {
    val ownerKey: String? get() = if (tokenJwt.isBlank()) null else "$famId:$posId"

    companion object {
        val EMPTY = SettingsUserInfoDomModel("", 0, 0, null, null, false)
    }
}
