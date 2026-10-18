package ru.ama.whereme.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Failed : LoadState<Nothing>
}

/** Retry re-subscribes; a read failure is never disguised as default user settings. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.loadState(scope: CoroutineScope, retry: Flow<Int>): StateFlow<LoadState<T>> =
    retry.flatMapLatest {
        map<T, LoadState<T>> { LoadState.Ready(it) }
            .onStart { emit(LoadState.Loading) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(LoadState.Failed)
            }
    }.stateIn(scope, SharingStarted.Eagerly, LoadState.Loading)
