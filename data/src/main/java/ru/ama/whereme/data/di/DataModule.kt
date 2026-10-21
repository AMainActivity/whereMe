package ru.ama.whereme.data.di

import android.app.Application
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.ama.whereme.data.BuildConfig
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.location.PlatformLocationSource
import ru.ama.whereme.data.location.RoomLocationRecorder
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.data.repository.*
import ru.ama.whereme.data.session.DataStoreSessionRepository
import ru.ama.whereme.data.settings.DataStoreSettingsRepository
import ru.ama.whereme.data.sync.LocationBatchUploader
import ru.ama.whereme.data.sync.PendingLocationStore
import ru.ama.whereme.data.sync.RoomPendingLocationStore
import ru.ama.whereme.data.tracking.DataStoreRecordingSessionStore
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.domain.tracking.LocationRecorder
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import ru.ama.whereme.domain.tracking.RecordingSessionStore

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindLegacyHistory(impl: ru.ama.whereme.data.database.RoomLegacyHistoryAssignment): ru.ama.whereme.data.database.LegacyHistoryAssignment

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
    fun bindPendingLocationStore(impl: RoomPendingLocationStore): PendingLocationStore

    companion object {
        private const val DATABASE_NAME = "main.db"
        private const val JSON_MEDIA_TYPE = "application/json"

        @Provides
        @Singleton
        fun provideDatabase(application: Application): AppDatabase = Room.databaseBuilder(application, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_3_4).build()

        @Provides
        @Singleton
        fun provideLocationDao(database: AppDatabase): LocationDao = database.locationDao()

        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            // Preserve the existing backend parser tolerance; stored profiles use strict JSON.
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        @Provides @Singleton
        fun provideApiService(json: Json): WmApiService = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL).addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build().create(WmApiService::class.java)

    }
}
