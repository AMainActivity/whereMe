package ru.ama.whereme.presentation

import android.app.Application
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ru.ama.whereme.R
import ru.ama.whereme.domain.usecase.GetJwTokenUseCase
import ru.ama.whereme.domain.usecase.RefreshSessionUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getJwTokenUseCase: GetJwTokenUseCase,
    private val refreshSessionUseCase: RefreshSessionUseCase,
    private val application: Application
) : ViewModel() {
    init {
        val session = getJwTokenUseCase()
        if (session.tokenJwt.isNotBlank()) {
            viewModelScope.launch {
                val request = JSONObject()
                    .put(application.getString(R.string.profile_in_json_param), session.tokenJwt)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                refreshSessionUseCase(session, request)
            }
        }
    }

    fun checkIsActivate() = getJwTokenUseCase().isActivate
}
