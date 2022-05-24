package ru.ama.whereme.di

import androidx.lifecycle.ViewModel

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import ru.ama.whereme.presentation.TestListViewModel

@Module
interface ViewModelModule {
 @Binds
    @IntoMap
    @ViewModelKey(TestListViewModel::class)
    fun bindTestListViewModel(viewModel: TestListViewModel): ViewModel
}
