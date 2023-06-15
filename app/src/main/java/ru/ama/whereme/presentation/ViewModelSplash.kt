package ru.ama.whereme.presentationn

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class ViewModelSplash @Inject constructor(
    private val checkInternetConnectionUseCase: CheckInternetConnectionUseCase,
    private val checkJwtTokenUseCase: CheckJwtTokenUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val application: Application
) : ViewModel() {
    private lateinit var wmTokenInfoModel: SettingsUserInfoDomModel

    private val _canStart = MutableLiveData<Unit>()
    val canStart: LiveData<Unit>
        get() = _canStart

    init {
        Log.e("tokenJwt", getJwTokenUseCase().tokenJwt)
        if (!checkInternetConnectionUseCase()) _canStart.value = Unit
        else checkJwt(getJwTokenUseCase().tokenJwt)
    }

    private fun checkJwt(kod: String) {
        val json = JSONObject()
        json.put(application.getString(R.string.profile_in_json_param), kod)
        val requestBody: RequestBody =
            RequestBody.create(MediaType.parse(APPLICATION_JSON), json.toString())
        Log.e("checkJwt1", json.toString())
        viewModelScope.launch {
            val response = checkJwtTokenUseCase(requestBody)
            Log.e("checkJwtCode", response.respCode.toString())
            Log.e("checkJwt", response.toString())
            Log.e("mBody", response.mBody.toString())
            if (response.respIsSuccess) {
                wmTokenInfoModel = getJwTokenUseCase()
                response.mBody?.let {
                    setWmJwTokenUseCase(
                        wmTokenInfoModel.copy(
                            isActivate = (it.message) == ONE_UNIT
                        )
                    )
                }
                _canStart.value = Unit
            } else {
                try {
                    val jObjError = response.respError?.string()?.let { JSONObject(it) }
                    Log.e(
                        "checkJwtError",
                        jObjError.toString()/*.getJSONObject("error").getString("message")*/
                    )
                } catch (e: Exception) {
                    Log.e("checkJwtError", e.message.toString())
                }
                _canStart.value = Unit
                setWmJwTokenUseCase(
                    SettingsUserInfoDomModel(
                        EMPTY_STRING, ZERO_INT, ZERO_INT, EMPTY_STRING, EMPTY_STRING, false
                    )
                )
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