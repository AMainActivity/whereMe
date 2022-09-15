package ru.ama.whereme.presentation


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import ru.ama.whereme.domain.usecase.CheckKodUseCase
import javax.inject.Inject

class ProfileViewModel @Inject constructor(
    private val checkKodUseCase: CheckKodUseCase
) : ViewModel() {

    init {

    }

fun checkKod(kod:String)
{val json = JSONObject()
        json.put("kod", kod)
        val requestBody: RequestBody = RequestBody.create(MediaType.parse("application/json"), json.toString())

    Log.e("response1",json.toString())
 viewModelScope.launch {
     val response = checkKodUseCase(requestBody)
     Log.e("response",response.toString())
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