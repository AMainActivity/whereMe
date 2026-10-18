package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.usecase.RefreshSessionUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    sessions: SessionRepository, refresh: RefreshSessionUseCase
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val session = sessions.session.loadState(viewModelScope, retry)
    init {
        viewModelScope.launch {
            session.filterIsInstance<LoadState.Ready<SessionSnapshot>>()
                .distinctUntilChangedBy { it.value.generation }
                .collectLatest { refresh(it.value) }
        }
    }
    fun retrySession() { retry.value++ }
}
