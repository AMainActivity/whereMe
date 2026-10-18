package ru.ama.whereme.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.JsonJwt
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.usecase.CheckKodUseCase
import javax.inject.Inject

@HiltViewModel
class ProfileInViewModel @Inject constructor(
    private val checkCode: CheckKodUseCase, private val sessions: SessionRepository
) : ViewModel() {
    private val success = MutableLiveData<JsonJwt>()
    val isSuccess: LiveData<JsonJwt> = success
    private val error = MutableLiveData<Int>()
    val isError: LiveData<Int> = error
    private var operation: Job? = null

    fun saveUserInfo(value: JsonJwt) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                sessions.signIn(SettingsUserInfoDomModel(value.tokenJwt, value.posId, value.famId,
                    value.name, value.url, value.isActivate == 1))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error.value = R.string.storage_write_failed }
        }
    }
    fun checkKod(code: String) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                val result = checkCode(code)
                val body = result.mBody
                if (result.respIsSuccess && body != null && !body.error && body.message == "1" && body.tokenJwt.isNotBlank()) {
                    success.value = body
                } else error.value = R.string.profile_in_error_kod
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error.value = R.string.profile_request_failed }
        }
    }
}
