package ru.ama.whereme.di

import ru.ama.whereme.data.workers.ChildWorkerFactory
import ru.ama.whereme.data.workers.GetLocationDataWorker
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
interface WorkerModule {

    @Binds
    @IntoMap
    @WorkerKey(GetLocationDataWorker::class)
    fun bindRefreshDataWorkerFactory(worker: GetLocationDataWorker.Factory): ChildWorkerFactory
}
