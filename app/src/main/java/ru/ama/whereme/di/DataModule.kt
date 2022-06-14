package ru.ama.whereme.diO

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
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
       /* @Provides
        @ApplicationScope
        fun provideLocationLiveData(
            application: Application
        ) : LocationLiveData {
            return LocationServices.getFusedLocationProviderClient(application)
        }*/

        @Provides
        @ApplicationScope
        fun provideFusedLocationProviderClient(
            application: Application
        ) :FusedLocationProviderClient{
            return LocationServices.getFusedLocationProviderClient(application)
        }
        @Provides
        @ApplicationScope
        fun provideGoogleApiAvailability() = GoogleApiAvailability.getInstance()

    @Provides
    @ApplicationScope
    fun provideDataStore(application: Application): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            application.preferencesDataStoreFile("prefs")
        }
    }
        @ApplicationScope
        @Provides
        fun providesCoroutineScope() = CoroutineScope(SupervisorJob() +  Dispatchers.IO)
       /* @Provides
        @ApplicationScope
        fun provideCoroutineScope(application: Application)= CoroutineScope(application as CoroutineContext)
*/

    }
	
	/*
	  @Provides
    @Singleton
    fun provideGoogleApiAvailability() = GoogleApiAvailability.getInstance()

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        application: Application
    ) = LocationServices.getFusedLocationProviderClient(application)

    @Provides
    @Singleton
    fun provideDataStore(application: Application): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            application.preferencesDataStoreFile("prefs")
        }
    }
	*/
}
