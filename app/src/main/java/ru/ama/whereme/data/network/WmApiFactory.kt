package ru.ama.whereme.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.ama.ottest.data.network.WmApiService

object WmApiFactory {

    private const val BASE_URL = "https://kol.hhos.ru/gkk/"

    private val retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(BASE_URL)
        .build()

    val apiService = retrofit.create(WmApiService::class.java)
}