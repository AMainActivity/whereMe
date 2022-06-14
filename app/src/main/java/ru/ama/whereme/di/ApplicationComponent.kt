package ru.ama.whereme.di


import android.app.Application
import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Component
import ru.ama.whereme.di.ViewModelModule
import ru.ama.whereme.diO.DataModule
import ru.ama.whereme.presentation.FirstFragment
import ru.ama.whereme.presentation.MainActivity
import ru.ama.whereme.presentation.MyApp
import ru.ama.whereme.presentation.MyForegroundService

@ApplicationScope
@Component(
    modules = [
        DataModule::class,
        ViewModelModule::class,
        WorkerModule::class
    ]
)
interface ApplicationComponent {

    fun inject(activity: MainActivity)
    fun inject(fragment: FirstFragment)
    fun inject(myForegroundService: MyForegroundService)
    fun inject(application: MyApp)


    @Component.Factory
    interface Factory {

        fun create(
            @BindsInstance application: Application
        ): ApplicationComponent
    }
}