package ru.ama.whereme.presentation

import ru.ama.whereme.domain.reportFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Failed : LoadState<Nothing>
}

/** Failures are state, never invented default data. Cancellation still belongs to the caller. */
fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> =
    map<T, LoadState<T>> { LoadState.Ready(it) }
        .onStart { emit(LoadState.Loading) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            reportFailure("Read screen data", failure)
            emit(LoadState.Failed)
        }

/** Retry re-subscribes to the source. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.loadState(scope: CoroutineScope, retry: Flow<Int>): StateFlow<LoadState<T>> =
    retry.flatMapLatest { asLoadState() }.stateIn(scope, SharingStarted.Eagerly, LoadState.Loading)
