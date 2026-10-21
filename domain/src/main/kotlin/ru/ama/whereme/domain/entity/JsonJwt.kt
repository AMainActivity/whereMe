package ru.ama.whereme.domain.entity


data class JsonJwt(
    val error: Boolean,
    val message: String,
    val tokenJwt: String,
    val posId: Int,
    val famId: Int,
    val name: String? = null,
    val url: String? = null,
    val isActivate: Int
) {
    companion object {
        const val ACTIVE = 1
    }
}
