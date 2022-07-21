package ru.ama.whereme.di

import androidx.lifecycle.ViewModel

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import ru.ama.whereme.presentation.MapViewModel
import ru.ama.whereme.presentation.MaViewModel

@Module
interface ViewModelModule {
 @Binds
    @IntoMap
    @ViewModelKey(MaViewModel::class)
    fun bindTestListViewModel(viewModel: MaViewModel): ViewModel
 @Binds
    @IntoMap
    @ViewModelKey(MapViewModel::class)
    fun bindMapViewModel(viewModel: MapViewModel): ViewModel
 /*@Binds
    @IntoMap
    @ViewModelKey(ServiceViewModel::class)
    fun bindServiceViewModel(viewModel: ServiceViewModel): ViewModel*/
}
