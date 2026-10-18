package ru.ama.whereme.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.network.WmApiFactory
import ru.ama.whereme.data.sync.PendingLocationStore
import ru.ama.whereme.data.sync.RoomPendingLocationStore
import ru.ama.whereme.data.repository.WmRepositoryImpl
import javax.inject.Singleton
import ru.ama.whereme.domain.repository.WmRepository

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindCoinRepository(impl: WmRepositoryImpl): WmRepository

    @Binds
    fun bindPendingLocationStore(impl: RoomPendingLocationStore): PendingLocationStore

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(application: Application): AppDatabase = AppDatabase.getInstance(application)

        @Provides
        @Singleton
        fun provideLocationDao(database: AppDatabase): LocationDao = database.locationDao()

        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(
            application: Application
        ): FusedLocationProviderClient {
            return LocationServices.getFusedLocationProviderClient(application)
        }

        @Provides
        @Singleton
        fun provideGoogleApiAvailability() = GoogleApiAvailability.getInstance()

        @Provides
        @Singleton
        fun provideApiService(): WmApiService {
            return WmApiFactory.apiService
        }

        @Provides
        @Singleton
        fun provideSharedPreferences(application: Application): SharedPreferences {
            return application.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        }

        const val sharedPrefName = "mysettings"
    }
}