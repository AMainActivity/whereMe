package ru.ama.whereme.presentationn

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
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class ViewModelSplash @Inject constructor(
    private val checkJwtTokenUseCase: CheckJwtTokenUseCase,
    private val getJwtFromSetingsUseCase: GetJwtFromSetingsUseCase,
    private val getWorkingTimeUseCase: GetWorkingTimeUseCase,
    private val setWmJwTokenUseCase: SetJwTokenUseCase,
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val repositoryImpl: WmRepositoryImpl
   // private val setIsActivateUseCase: SetIsActivateUseCase
) : ViewModel() {
    private lateinit var wmTokenInfoModel: SettingsUserInfoDomModel

    init {
        checkJwt()
		wmTokenInfoModel=getJwTokenUseCase()
        Log.e("datetime",repositoryImpl.df())
        viewModelScope.launch(Dispatchers.IO) {  Log.e("getLastValue1",repositoryImpl.getLastValue1().toString())}
    }

    private val _canStart = MutableLiveData<Unit>()
    val canStart: LiveData<Unit>
        get() = _canStart



    fun checkJwt()
    {val json = JSONObject()
        json.put("kod", getJwtFromSetingsUseCase().tokenJwt)
        val requestBody: RequestBody = RequestBody.create(MediaType.parse("application/json"), json.toString())

        Log.e("checkJwt1",json.toString())
        viewModelScope.launch {
            val response = checkJwtTokenUseCase(requestBody)
            Log.e("checkJwtCode",response.respCode.toString())
            Log.e("checkJwt",response.toString())
            Log.e("mBody",response.mBody.toString())
            if (response.respIsSuccess) {
                response.mBody?.let {					
		setWmJwTokenUseCase(
		wmTokenInfoModel.copy(
                               isActivate =   (it.message).equals("1")
                            )
						)
		// setIsActivateUseCase(it.message.equals("1"))
                }
                _canStart.value=Unit
            }
            /*
checkJwt: ResponseEntity(mBody=JsonEntity(error=false, message=0), respIsSuccess=true, respError=null, respCode=200)
mBody: JsonEntity(error=false, message=0)
            * */
            else
            {
                try {
                    val jObjError = JSONObject(response.respError?.string())

                    Log.e("checkJwtError",jObjError.toString()/*.getJSONObject("error").getString("message")*/)
                } catch (e: Exception) {
                    Log.e("checkJwtError",e.message.toString())
                }
                _canStart.value=Unit
                setWmJwTokenUseCase(
                    SettingsUserInfoDomModel(
                        "",
                        0,
                        0,
                        "",
                        "",
                        false
                    )
                )}


        }
    }

    companion object {}
}
