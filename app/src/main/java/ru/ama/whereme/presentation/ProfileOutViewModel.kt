package ru.ama.whereme.presentation


import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject


class ProfileOutViewModel @Inject constructor(
    private val logOutUseCase: LogOutUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val application: Application
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
        val json = JSONObject()
        json.put(application.getString(R.string.profile_in_json_param), wmTokenInfoModel.tokenJwt)
        val requestBody: RequestBody =
            RequestBody.create(MediaType.parse(APPLICATION_JSON), json.toString())

        Log.e("response1", json.toString())
        viewModelScope.launch {
            val response = logOutUseCase(requestBody)
            Log.e("responseCode", response.respCode.toString())
            Log.e("response", response.toString())
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
                try {
                    val jObjError = response.respError?.string()?.let { JSONObject(it) }

                    Log.e(
                        "responseError",
                        jObjError.toString()/*.getJSONObject("error").getString("message")*/
                    )
                } catch (e: Exception) {
                    Log.e("responseError", e.message.toString())
                }
            }

        }
    }

    private companion object {
        private const val APPLICATION_JSON = "application/json"
        private const val ONE_UNIT = "1"
        private const val ZERO_INT = 0
        private const val EMPTY_STRING = ""
    }
}