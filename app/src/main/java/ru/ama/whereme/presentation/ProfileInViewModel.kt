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
import ru.ama.whereme.domain.entity.JsonJwt
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.CheckKodUseCase
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import ru.ama.whereme.domain.usecase.SetIsActivateUseCase
import ru.ama.whereme.domain.usecase.SetJwTokenUseCase
import javax.inject.Inject


class ProfileInViewModel @Inject constructor(
    private val checkKodUseCase: CheckKodUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val application: Application
) : ViewModel() {
    private val _isSuccess = MutableLiveData<JsonJwt>()
    val isSuccess: LiveData<JsonJwt>
        get() = _isSuccess
    private val _isError = MutableLiveData<Unit>()
    val isError: LiveData<Unit>
        get() = _isError
    init {
        Log.e("getJwTokenUseCase", getJwTokenUseCase().toString())
    }

    fun saveUserInfo(res:JsonJwt)
    {
        setWmJwTokenUseCase(SettingsUserInfoDomModel(
            res.tokenJwt,
            res.posId,
            res.famId,
            res.name,
            res.url,
            true
        ))
    }
    fun checkKod(kod: String) {
        val json = JSONObject()
        json.put("kod", kod)
        val requestBody: RequestBody =
            RequestBody.create(MediaType.parse("application/json"), json.toString())
        Log.e("response1", json.toString())
        viewModelScope.launch {
            val response = checkKodUseCase(requestBody)
            Log.e("responseCode", response.respCode.toString())
            Log.e("response", response.toString())
            if (response.respIsSuccess) {
                response.mBody?.let {
                    if (!it.error && it.message == "1") {
                        _isSuccess.value = it
                    } else
                        _isError.value=Unit
                }
            } else {
                _isError.value=Unit
                try {
                    val jObjError = response.respError?.string()?.let { JSONObject(it) }
                    Log.e(
                        "responseError",
                        jObjError.toString()
                    )
                } catch (e: Exception) {
                    Log.e("responseError", e.message.toString())
                }
            }
        }
    }
}