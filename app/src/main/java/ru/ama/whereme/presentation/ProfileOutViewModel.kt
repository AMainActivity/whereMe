package ru.ama.whereme.presentation


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
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject


class ProfileOutViewModel @Inject constructor(
    private val logOutUseCase: LogOutUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val setIsActivateUseCase: SetIsActivateUseCase
) : ViewModel() {

    private val _isSuccess = MutableLiveData<Unit>()
    val isSuccess: LiveData<Unit>
        get() = _isSuccess
    init {
        // Log.e("getJwTokenUseCase",getJwTokenUseCase().toString())
    }

fun logOut()
{val json = JSONObject()
        json.put("kod", getJwTokenUseCase())
        val requestBody: RequestBody = RequestBody.create(MediaType.parse("application/json"), json.toString())

    Log.e("response1",json.toString())
 viewModelScope.launch {
     val response = logOutUseCase(requestBody)
     Log.e("responseCode",response.respCode.toString())
     Log.e("response",response.toString())
     if (response.respIsSuccess) {
         response.mBody?.let { 
             if (it.error==false && it.message.equals("1"))
             {  setWmJwTokenUseCase("")
             setIsActivateUseCase(false)
             _isSuccess.value = Unit}
         }
     }
     else
     {
         try {
             val jObjError = JSONObject(response.respError?.string())

             Log.e("responseError",jObjError.toString()/*.getJSONObject("error").getString("message")*/)
         } catch (e: Exception) {
             Log.e("responseError",e.message.toString())
         }}

        }
}
}