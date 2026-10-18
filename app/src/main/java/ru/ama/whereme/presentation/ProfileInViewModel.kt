package ru.ama.whereme.presentation


import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.JsonJwt
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.CheckKodUseCase
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import ru.ama.whereme.domain.usecase.SetJwTokenUseCase
import javax.inject.Inject


@HiltViewModel
class ProfileInViewModel @Inject constructor(
    private val checkKodUseCase: CheckKodUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase
) : ViewModel() {
    private val _isSuccess = MutableLiveData<JsonJwt>()
    val isSuccess: LiveData<JsonJwt>
        get() = _isSuccess
    private val _isError = MutableLiveData<Unit>()
    val isError: LiveData<Unit>
        get() = _isError

    fun saveUserInfo(res: JsonJwt) {
        setWmJwTokenUseCase(
            SettingsUserInfoDomModel(
                res.tokenJwt,
                res.posId,
                res.famId,
                res.name,
                res.url,
                true
            )
        )
    }

    fun checkKod(kod: String) {
        viewModelScope.launch {
            val response = checkKodUseCase(kod)
            Log.e("responseCode", response.respCode.toString())
            if (response.respIsSuccess) {
                response.mBody?.let {
                    if (!it.error && it.message == ONE_UNIT) {
                        _isSuccess.value = it
                    } else
                        _isError.value = Unit
                }
            } else {
                _isError.value = Unit
            }
        }
    }

    private companion object {
        private const val ONE_UNIT = "1"
    }
}