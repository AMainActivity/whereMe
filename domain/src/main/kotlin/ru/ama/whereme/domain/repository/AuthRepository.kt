package ru.ama.whereme.domain.repository

import ru.ama.whereme.domain.entity.*

interface AuthRepository {
    suspend fun checkKod(code: String): ResponseJwtEntity
    suspend fun checkWmJwToken(token: String): ResponseEntity
    suspend fun logOut(token: String): ResponseEntity
}
