package ru.ama.whereme.presentationn

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import ru.ama.whereme.domain.usecase.CheckJwtTokenUseCase
import ru.ama.whereme.domain.usecase.CheckKodUseCase
import ru.ama.whereme.domain.usecase.GetJwtFromSetingsUseCase
import ru.ama.whereme.domain.usecase.SetIsActivateUseCase
import javax.inject.Inject

class ViewModelSplash @Inject constructor(
    private val checkJwtTokenUseCase: CheckJwtTokenUseCase,
    private val getJwtFromSetingsUseCase: GetJwtFromSetingsUseCase,
    private val setIsActivateUseCase: SetIsActivateUseCase
) : ViewModel() {

    init {
        checkJwt()
    }

    private val _canStart = MutableLiveData<Unit>()
    val canStart: LiveData<Unit>
        get() = _canStart



    fun checkJwt()
    {val json = JSONObject()
        json.put("kod", getJwtFromSetingsUseCase())
        val requestBody: RequestBody = RequestBody.create(MediaType.parse("application/json"), json.toString())

        Log.e("checkJwt1",json.toString())
        viewModelScope.launch {
            val response = checkJwtTokenUseCase(requestBody)
            Log.e("checkJwtCode",response.respCode.toString())
            Log.e("checkJwt",response.toString())
            if (response.respIsSuccess) {
                response.mBody?.let {					
		 setIsActivateUseCase(it.message.equals("1"))
                }
            }
            else
            {
                try {
                    val jObjError = JSONObject(response.respError?.string())

                    Log.e("checkJwtError",jObjError.toString()/*.getJSONObject("error").getString("message")*/)
                } catch (e: Exception) {
                    Log.e("checkJwtError",e.message.toString())
                }}


        }
    }

    companion object {}
}
