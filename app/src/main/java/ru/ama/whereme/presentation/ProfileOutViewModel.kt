package ru.ama.whereme.presentation


import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject


@HiltViewModel
class ProfileOutViewModel @Inject constructor(
    private val logOutUseCase: LogOutUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase
) : ViewModel() {

    private var wmTokenInfoModel: SettingsUserInfoDomModel
    private val _isSuccess = MutableLiveData<Unit>()
    val isSuccess: LiveData<Unit>
        get() = _isSuccess

    init {
        wmTokenInfoModel = getJwTokenUseCase()
    }

    fun getSetUserInfo() = wmTokenInfoModel


    fun logOut() {
        viewModelScope.launch {
            val response = logOutUseCase(wmTokenInfoModel.tokenJwt)
            Log.e("responseCode", response.respCode.toString())
            if (response.respIsSuccess) {
                response.mBody?.let {
                    if (!it.error && it.message == ONE_UNIT) {
                        setWmJwTokenUseCase(
                            SettingsUserInfoDomModel(
                                EMPTY_STRING,
                                ZERO_INT,
                                ZERO_INT,
                                EMPTY_STRING,
                                EMPTY_STRING,
                                false
                            )
                        )
                        _isSuccess.value = Unit
                    }
                }
            } else {
            }

        }
    }

    private companion object {
        private const val ONE_UNIT = "1"
        private const val ZERO_INT = 0
        private const val EMPTY_STRING = ""
    }
}