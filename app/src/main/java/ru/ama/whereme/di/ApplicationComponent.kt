package ru.ama.whereme.di


import android.app.Application
import dagger.BindsInstance
import dagger.Component
import ru.ama.whereme.data.workers.AlarmClockStart
import ru.ama.whereme.data.workers.StartServiceReceiver
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
    fun inject(startServiceReceiver: StartServiceReceiver)
    fun inject(alarmClockStart: AlarmClockStart)
    fun inject(fragment: SettingsFragment)
    fun inject(fragment: MapFragment)
    fun inject(fragment: ProfileFragment)
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