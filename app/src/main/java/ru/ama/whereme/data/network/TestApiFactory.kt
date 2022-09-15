package ru.ama.whereme.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.ama.ottest.data.network.TestApiService

object TestApiFactory {

    private const val BASE_URL = "https://kol.hhos.ru/gkk/"

    private val retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(BASE_URL)
        .build()

    val apiService = retrofit.create(TestApiService::class.java)
}