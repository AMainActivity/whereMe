package ru.ama.whereme.domain.entity


data class JsonEntity(
    val error: Boolean,
    val message: String
) {
    companion object {
        const val POSITIVE_MESSAGE = "1"
        const val NEGATIVE_MESSAGE = "0"
    }
}
