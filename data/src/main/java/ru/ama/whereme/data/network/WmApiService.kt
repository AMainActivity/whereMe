package ru.ama.whereme.data.network

import ru.ama.whereme.data.network.model.CredentialRequest
import ru.ama.whereme.data.network.model.DatasToJson
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import ru.ama.whereme.data.BuildConfig
import ru.ama.whereme.data.network.model.JsonDto
import ru.ama.whereme.data.network.model.JsonJwtDto

interface WmApiService {

    @POST(BuildConfig.GKK_CHECK_KOD)
    suspend fun chekcKod(
        @Body request: CredentialRequest
    ): Response<JsonJwtDto>

    @POST(BuildConfig.GKK_CHECK_TOKEN)
    suspend fun checkToken(
        @Body request: CredentialRequest
    ): Response<JsonDto>

    @POST(BuildConfig.GKK_LOG_OUT)
    suspend fun logOut(
        @Body request: CredentialRequest
    ): Response<JsonDto>

    @POST(BuildConfig.GKK_WRITE_LOC_DATAS)
    suspend fun writeLocDatas(
        @Body request: DatasToJson
    ): Response<JsonDto>
}