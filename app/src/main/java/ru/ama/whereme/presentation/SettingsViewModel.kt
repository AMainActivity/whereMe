package ru.ama.whereme.presentation

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val getWorkingTimeUseCase: GetWorkingTimeUseCase,
    private val setWorkingTimeUseCase: SetWorkingTimeUseCase

) : ViewModel() {

    init {
        Log.e("SettingsViewModel",getWorkingTimeUseCase().toString())
    }


/*
var jsonString = gson.toJson(TestModel(1,"Test"))
{"id":1,"description":"Test"}
var testModel = gson.fromJson(jsonString, TestModel::class.java)
data class TestModel(
    val id: Int,
    val description: String
)
*/

    companion object {}
}