package ru.ama.whereme.di


import android.app.Application
import dagger.BindsInstance
import dagger.Component
import ru.ama.whereme.di.ViewModelModule
import ru.ama.whereme.diO.DataModule
import ru.ama.whereme.presentation.MainActivity
import ru.ama.whereme.presentation.MyApp

@ApplicationScope
@Component(
    modules = [
       // DataModule::class,
       // ViewModelModule::class
    ]
)
interface ApplicationComponent {

    fun inject(activity: MainActivity)
   // fun inject(application: MyApp)


    @Component.Factory
    interface Factory {

        fun create(
            @BindsInstance application: Application
        ): ApplicationComponent
    }
}