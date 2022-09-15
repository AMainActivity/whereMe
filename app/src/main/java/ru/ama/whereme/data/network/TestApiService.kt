package ru.ama.ottest.data.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import ru.ama.whereme.data.network.model.JsonJwtDto

interface TestApiService {

    @POST("gkk_ChechKod.php")
    suspend fun getTestById(
        @Body request : RequestBody
    ): Response<JsonJwtDto>

    
    companion object {
    }
}