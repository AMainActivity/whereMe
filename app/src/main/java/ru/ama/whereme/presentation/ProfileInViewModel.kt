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
    //private val setIsActivateUseCase: SetIsActivateUseCase,
    private val application: Application
) : ViewModel() {

    private val _isSuccess = MutableLiveData<Unit>()
    val isSuccess: LiveData<Unit>
        get() = _isSuccess

    init {
        Log.e("getJwTokenUseCase", getJwTokenUseCase().toString())
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
                    if (it.error == false && it.message.equals("1")) {
                        setWmJwTokenUseCase(SettingsUserInfoDomModel(
                            it.tokenJwt,
                            it.posId,
                            it.famId,
                            it.name,
                            it.url,
                            it.isActivate =i
                        ))
                      //  setIsActivateUseCase(it.isActivate == 1)
                        _isSuccess.value = Unit
                    } else
                        Toast.makeText(
                            application,
                            "неверный код, повторите попытку",
                            Toast.LENGTH_SHORT
                        ).show()
                }
            } else {
                Toast.makeText(application, "неверный код, повторите попытку", Toast.LENGTH_SHORT)
                    .show()

                try {
                    val jObjError = JSONObject(response.respError?.string())

                    Log.e(
                        "responseError",
                        jObjError.toString()/*.getJSONObject("error").getString("message")*/
                    )
                } catch (e: Exception) {
                    Log.e("responseError", e.message.toString())
                }
            }


            /* try {.
                 if (response.isSuccessful()) {
                    Log.e("response",response.toString())


                 } else {
                     Toast.makeText(
                         this@MainActivity,
                         response.errorBody().toString(),
                         Toast.LENGTH_LONG
                     ).show()
                 }
             }catch (Ex:Exception){
                 Log.e("Error",Ex.localizedMessage)
             }*/
        }
    }
}