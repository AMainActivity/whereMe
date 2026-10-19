package ru.ama.whereme.data.repository

import ru.ama.whereme.data.mapper.WmMapperJwt
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.data.network.model.CredentialRequest
import ru.ama.whereme.domain.entity.ResponseEntity
import ru.ama.whereme.domain.entity.ResponseJwtEntity
import ru.ama.whereme.domain.repository.AuthRepository
import javax.inject.Inject

class RemoteAuthRepository @Inject constructor(
    private val apiService: WmApiService,
    private val mapper: WmMapperJwt
) : AuthRepository {
    override suspend fun checkKod(code: String): ResponseJwtEntity {
        val response = apiService.chekcKod(CredentialRequest(code))
        return try {
            ResponseJwtEntity(response.body()?.let(mapper::mapDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }

    override suspend fun checkWmJwToken(token: String): ResponseEntity {
        val response = apiService.checkToken(CredentialRequest(token))
        return try {
            ResponseEntity(response.body()?.let(mapper::mapAllDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }

    override suspend fun logOut(token: String): ResponseEntity {
        val response = apiService.logOut(CredentialRequest(token))
        return try {
            ResponseEntity(response.body()?.let(mapper::mapAllDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }
}
