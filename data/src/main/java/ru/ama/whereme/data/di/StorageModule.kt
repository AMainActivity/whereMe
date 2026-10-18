package ru.ama.whereme.data.di

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ama.whereme.data.session.*
import ru.ama.whereme.data.settings.*
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SettingsStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SessionStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton @ApplicationScope
    fun provideScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideCipher(keys: AndroidSessionKeyProvider): SessionCipher = SessionCipher(keys)

    @Provides @Singleton @SettingsStore
    fun provideSettings(application: Application,
        @ApplicationScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { application.preferencesDataStoreFile("settings") })

    @Provides @Singleton @SessionStore
    fun provideSession(application: Application,
        @ApplicationScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(application.noBackupFilesDir, "session.preferences_pb") })
}
