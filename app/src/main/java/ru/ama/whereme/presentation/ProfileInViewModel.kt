package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.JsonJwt
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.repository.AuthRepository
import javax.inject.Inject

sealed interface SignInState {
    data object Editing : SignInState
    data object Checking : SignInState
    data class Confirmation(val profile: JsonJwt) : SignInState
    data object Saving : SignInState
    data object SignedIn : SignInState
    data class Failed(val message: Int) : SignInState
}

@HiltViewModel
class ProfileInViewModel @Inject constructor(
    private val checkCode: AuthRepository, private val sessions: SessionRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<SignInState>(SignInState.Editing)
    val state = mutableState.asStateFlow()

    fun dismissConfirmation() {
        if (state.value is SignInState.Confirmation) mutableState.value = SignInState.Editing
    }

    fun confirmSignIn() {
        val value = (state.value as? SignInState.Confirmation)?.profile ?: return
        mutableState.value = SignInState.Saving
        viewModelScope.launch {
            try {
                sessions.signIn(SettingsUserInfoDomModel(value.tokenJwt, value.posId, value.famId,
                    value.name, value.url, value.isActivate == 1))
                mutableState.value = SignInState.SignedIn
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = SignInState.Failed(R.string.storage_write_failed) }
        }
    }

    fun checkKod(code: String) {
        if (state.value != SignInState.Editing && state.value !is SignInState.Failed) return
        mutableState.value = SignInState.Checking
        viewModelScope.launch {
            try {
                val result = checkCode.checkKod(code)
                val body = result.mBody
                mutableState.value = if (result.respIsSuccess && body != null && !body.error &&
                    body.message == "1" && body.tokenJwt.isNotBlank()) SignInState.Confirmation(body)
                else SignInState.Failed(R.string.profile_in_error_kod)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = SignInState.Failed(R.string.profile_request_failed) }
        }
    }
}
