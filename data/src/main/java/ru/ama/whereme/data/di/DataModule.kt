package ru.ama.whereme.data.di

import android.app.Application
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import ru.ama.whereme.data.location.PlatformLocationSource
import ru.ama.whereme.data.location.RoomLocationRecorder
import ru.ama.whereme.data.sync.LocationBatchUploader
import ru.ama.whereme.data.tracking.DataStoreRecordingSessionStore
import ru.ama.whereme.domain.tracking.RecordingSessionStore
import ru.ama.whereme.domain.tracking.LocationRecorder
import ru.ama.whereme.domain.tracking.PendingLocationUploader
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
    fun bindRecordingStore(impl: DataStoreRecordingSessionStore): RecordingSessionStore

    @Binds
    fun bindLocationRecorder(impl: RoomLocationRecorder): LocationRecorder

    @Binds
    fun bindLocationUploader(impl: LocationBatchUploader): PendingLocationUploader

    @Binds
    fun bindLocationSource(impl: PlatformLocationSource): LocationSource

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
