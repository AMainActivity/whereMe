package ru.ama.whereme.data.network

import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.BuildConfig


object WmApiFactory {

    private const val BASE_URL = BuildConfig.BASE_URL
    var gson = GsonBuilder()
        .setLenient()
        .create()
    private val retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create(gson))
        .baseUrl(BASE_URL)
        .build()

    val apiService = retrofit.create(WmApiService::class.java)
}