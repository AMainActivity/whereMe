package ru.ama.whereme.data.di

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ama.whereme.data.session.*
import ru.ama.whereme.data.settings.*

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SettingsStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SessionStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RecordingStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    private const val SETTINGS_STORE_NAME = "settings"
    private const val SESSION_FILE_NAME = "session.preferences_pb"
    private const val RECORDING_FILE_NAME = "recording.preferences_pb"
    @Provides @Singleton @RecordingStore
    fun provideRecording(application: Application,
        @ApplicationScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(application.noBackupFilesDir, RECORDING_FILE_NAME) })

    @Provides @Singleton @ApplicationScope
    fun provideScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideCipher(keys: AndroidSessionKeyProvider): SessionCipher = SessionCipher(keys)

    @Provides @Singleton @SettingsStore
    fun provideSettings(application: Application,
        @ApplicationScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { application.preferencesDataStoreFile(SETTINGS_STORE_NAME) })

    @Provides @Singleton @SessionStore
    fun provideSession(application: Application,
        @ApplicationScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(application.noBackupFilesDir, SESSION_FILE_NAME) })
}
