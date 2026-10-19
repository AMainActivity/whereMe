package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.repository.AuthRepository
import javax.inject.Inject

@HiltViewModel
class ProfileOutViewModel @Inject constructor(
    private val logout: AuthRepository, private val sessions: SessionRepository
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val session = sessions.session.loadState(viewModelScope, retry)
    private val error = MutableStateFlow<Int?>(null)
    val errorMessage = error.asStateFlow()
    private var operation: Job? = null
    fun retrySession() { retry.value++ }
    fun getSetUserInfo() = (session.value as? LoadState.Ready<SessionSnapshot>)?.value?.profile
    fun logOut() {
        if (operation?.isActive == true) return
        val expected = (session.value as? LoadState.Ready<SessionSnapshot>)?.value ?: return
        error.value = null
        operation = viewModelScope.launch {
            try {
                val response = logout.logOut(expected.profile.tokenJwt)
                val body = response.mBody
                if (response.respIsSuccess && body != null && !body.error && body.message == "1") {
                    sessions.clearIfCurrent(expected)
                } else error.value = R.string.profile_request_failed
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error.value = R.string.profile_request_failed }
        }
    }
}
