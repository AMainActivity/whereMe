package ru.ama.whereme.presentation

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import ru.ama.whereme.di.DaggerApplicationComponent


class MyApp : Application()  {


        val component by lazy {
            DaggerApplicationComponent.factory().create(this)
        }
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(base)
    }
	override fun onCreate() {
        component.inject(this)
        super.onCreate()
    }


    }
