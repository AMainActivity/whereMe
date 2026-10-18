package ru.ama.whereme.data.di

import android.app.Application
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.network.WmApiFactory
import ru.ama.whereme.data.sync.PendingLocationStore
import ru.ama.whereme.data.sync.RoomPendingLocationStore
import ru.ama.whereme.data.repository.*
import ru.ama.whereme.data.settings.DataStoreSettingsRepository
import ru.ama.whereme.data.session.DataStoreSessionRepository
import javax.inject.Singleton
import ru.ama.whereme.domain.repository.*

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindLocationSource(impl: ru.ama.whereme.data.location.PlatformLocationSource): LocationSource

    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    fun bindSessionRepository(impl: DataStoreSessionRepository): SessionRepository

    @Binds
    fun bindAuthRepository(impl: RemoteAuthRepository): AuthRepository

    @Binds
    fun bindLocationHistoryRepository(impl: RoomLocationHistoryRepository): LocationHistoryRepository

    @Binds
    fun bindNetworkStatus(impl: AndroidNetworkStatus): NetworkStatus


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
        fun provideApiService(): WmApiService {
            return WmApiFactory.apiService
        }

    }
}
