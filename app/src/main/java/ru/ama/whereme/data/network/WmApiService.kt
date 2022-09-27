package ru.ama.ottest.data.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import ru.ama.whereme.data.network.model.JsonDto
import ru.ama.whereme.data.network.model.JsonJwtDto

interface WmApiService {

    @POST("gkk_ChechKod.php")
    suspend fun chekcKod(
        @Body request: RequestBody
    ): Response<JsonJwtDto>

    @POST("gkk_checkToken.php")
    suspend fun checkToken(
        @Body request: RequestBody
    ): Response<JsonDto>

    @POST("gkk_Logout.php")
    suspend fun logOut(
        @Body request: RequestBody
    ): Response<JsonDto>

    @POST("gkk_WriteLocDatas.php")
    suspend fun writeLocDatas(
        @Body request: RequestBody
    ): Response<JsonDto>
	
	
	
    companion object {
    }
}