package ru.ama.whereme.presentation

import android.app.Application
import androidx.work.Configuration
import ru.ama.whereme.data.workers.CoinWorkerFactory
import ru.ama.whereme.di.DaggerApplicationComponent
import javax.inject.Inject


class MyApp : Application() , Configuration.Provider {

    @Inject
    lateinit var workerFactory: CoinWorkerFactory

        val component by lazy {
            DaggerApplicationComponent.factory().create(this)
        }

	override fun onCreate() {
        component.inject(this)
        super.onCreate()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
    }
