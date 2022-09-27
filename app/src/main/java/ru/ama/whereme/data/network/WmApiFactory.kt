package ru.ama.whereme.data.network

import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.ama.ottest.data.network.WmApiService


object WmApiFactory {

    private const val BASE_URL = "https://kol.hhos.ru/gkk/"
    var gson = GsonBuilder()
        .setLenient()
        .create()
    private val retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create(gson))
        .baseUrl(BASE_URL)
        .build()

    val apiService = retrofit.create(WmApiService::class.java)
}