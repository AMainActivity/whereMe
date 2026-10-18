package ru.ama.whereme.domain.entity


data class ResponseEntity(
    val mBody: JsonEntity? = null,
    val respIsSuccess: Boolean,
    val respCode: Int
)