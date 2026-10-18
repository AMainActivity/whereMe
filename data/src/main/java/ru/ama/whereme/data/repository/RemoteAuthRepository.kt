package ru.ama.whereme.data.repository

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private fun request(value: String) = Gson().toJson(CredentialRequest(value))
        .toRequestBody("application/json".toMediaType())

    override suspend fun checkKod(code: String): ResponseJwtEntity {
        val response = apiService.chekcKod(request(code))
        return try {
            ResponseJwtEntity(response.body()?.let(mapper::mapDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }

    override suspend fun checkWmJwToken(token: String): ResponseEntity {
        val response = apiService.checkToken(request(token))
        return try {
            ResponseEntity(response.body()?.let(mapper::mapAllDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }

    override suspend fun logOut(token: String): ResponseEntity {
        val response = apiService.logOut(request(token))
        return try {
            ResponseEntity(response.body()?.let(mapper::mapAllDtoToModel), response.isSuccessful, response.code())
        } finally {
            response.errorBody()?.close()
        }
    }
}
