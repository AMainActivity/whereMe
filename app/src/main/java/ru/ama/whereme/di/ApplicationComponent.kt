package ru.ama.whereme.di


import android.app.Application
import dagger.BindsInstance
import dagger.Component
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.data.alarms.AfterBootReceiver
import ru.ama.whereme.diO.DataModule
import ru.ama.whereme.presentation.*

@ApplicationScope
@Component(
    modules = [
        DataModule::class,
        ViewModelModule::class
    ]
)
interface ApplicationComponent {

    fun inject(activity: MainActivity)
    fun inject(activity: SplashActivity)
    fun inject(afterBootReceiver: AfterBootReceiver)
    fun inject(alarmClockStart: AlarmClockStart)
    fun inject(fragment: SettingsFragment)
    fun inject(fragment: MapFragment)
    fun inject(fragment: ProfileInFragment)
    fun inject(fragment: ProfileOutFragment)
    fun inject(fragment: AboutFragment)
    fun inject(myForegroundService: MyForegroundService)
    fun inject(application: MyApp)

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance application: Application
        ): ApplicationComponent
    }
}