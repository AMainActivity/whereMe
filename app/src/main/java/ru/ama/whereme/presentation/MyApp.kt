package ru.ama.whereme.presentation

import android.app.Application
import androidx.work.Configuration
import ru.ama.whereme.di.DaggerApplicationComponent
import javax.inject.Inject


class MyApp : Application()  {


        val component by lazy {
            DaggerApplicationComponent.factory().create(this)
        }

	override fun onCreate() {
        component.inject(this)
        super.onCreate()
    }


    }
