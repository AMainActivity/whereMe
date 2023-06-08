package ru.ama.whereme.diO

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.network.WmApiFactory
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.repository.WmRepository

@Module
interface DataModule {
    @Binds
    @ApplicationScope
    fun bindCoinRepository(impl: WmRepositoryImpl): WmRepository

    companion object {

        @Provides
        @ApplicationScope
        fun provideLocationDao(
            application: Application
        ): LocationDao {
            return AppDatabase.getInstance(application).locationDao()
        }

        @Provides
        @ApplicationScope
        fun provideFusedLocationProviderClient(
            application: Application
        ): FusedLocationProviderClient {
            return LocationServices.getFusedLocationProviderClient(application)
        }

        @Provides
        @ApplicationScope
        fun provideGoogleApiAvailability() = GoogleApiAvailability.getInstance()

        @Provides
        @ApplicationScope
        fun provideApiService(): WmApiService {
            return WmApiFactory.apiService
        }

        @Provides
        @ApplicationScope
        fun provideSharedPreferences(application: Application): SharedPreferences {
            return application.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        }

        @ApplicationScope
        @Provides
        fun providesCoroutineScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val sharedPrefName = "mysettings"
    }
}