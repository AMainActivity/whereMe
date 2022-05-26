package ru.ama.whereme.data.repository

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.sample.foregroundlocation.data.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.data.mapper.TestMapper
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.entity.TestInfo
import ru.ama.whereme.domain.entity.TestQuestion
import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject


class TestsRepositoryImpl @Inject constructor(
    private val mapper: TestMapper,
    private val locationDao: LocationDao,
    private val application: Application,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationScope private val externalScope: CoroutineScope
) : TestsRepository {

    private val callback = Callback()

    private val _infoLocation = MutableLiveData<Location?>()
    val infoLocation: LiveData<Location?>
        get() = _infoLocation

    override fun getQuestionsInfoList(testId: Int,limit:Int): List<TestQuestion>{
       var rl:MutableList<TestQuestion> = mutableListOf<TestQuestion>()
     /*   val list=testQuestionsDao.getQuestionListByTestId(testId,limit)
   	val llist2=list.map {
            mapper.mapDbModelToEntity(it)
        }

   */
		
        return rl

    }
   /*  fun getQuestionsInfoList2(): LiveData<List<TestQuestion>> {
        return Transformations.map(testQuestionsDao.getQuestionList()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }*/

    override fun getTestInfo(): List<TestInfo> {

		
			val rl= mutableListOf<TestInfo>()//(testInfoDao.getTestInfo()).map  {mapper.mapDataDbModelToEntity(it)}
		
        return rl
   }

    override suspend fun loadData():List<Int>  {
        var listOfItems:MutableList<Int> = mutableListOf<Int>()

			  return listOfItems
		
 
     
    }
    @SuppressLint("MissingPermission") // Only called when holding location permission.
    fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000 // 10 seconds
        }
        // Note: For this sample it's fine to use the main looper, so our callback will run on the
        // main thread. If your callback will perform any intensive operations (writing to disk,
        // making a network request, etc.), either change to a background thread from the callback,
        // or create a HandlerThread and pass its Looper here instead.
        // See https://developer.android.com/reference/android/os/HandlerThread.
        fusedLocationProviderClient.requestLocationUpdates(
            request,
            /*object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    Log.e("getLocation0",locationResult.lastLocation.toString())
                }}*/callback,
            Looper.getMainLooper()
        )
        Log.e("getLocation00",fusedLocationProviderClient.toString())
    }

    suspend fun inf()
    {
        Log.e("insertLocation2",infoLocation.value.toString())
        infoLocation.value?.let {
            val res= LocationDbModel(
                it.time.toString(),
                it.latitude.toLong(),
                it.longitude.toLong(),
                1,
                it.accuracy.toInt(),
                it.speed.toInt()
            )
            val itemsCount= locationDao.insertLocation(res)
            Log.e("insertLocation",res.toString())
        }
    }

    private inner class Callback: LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            _infoLocation.value = result.lastLocation
//externalScope.launch {  }
            ProcessLifecycleOwner.get().lifecycleScope.launch  {Log.e("insertLocation2",infoLocation.value.toString())
                result.lastLocation?.let {
                    val res= LocationDbModel(
                        it.time.toString(),
                        it.latitude.toLong(),
                        it.longitude.toLong(),
                        1,
                        it.accuracy.toInt(),
                        it.speed.toInt()
                    )
                    val itemsCount= locationDao.insertLocation(res)
                    Log.e("insertLocation",res.toString())
                }

            }
          /*  */

            Log.e("getLocation0",infoLocation.value.toString())
        }
    }
    fun stopLocationUpdates() {
        Log.e("getLocationStop",fusedLocationProviderClient.toString())
        _infoLocation.value = null
        fusedLocationProviderClient.removeLocationUpdates(callback)
    }

    override suspend fun getLocation(): LocationLiveData {
         val locationData = LocationLiveData(application)
        return locationData
     /*   locRepo.startLocationUpdates()
        Log.e("locrepo",locRepo.toString())
       return locRepo._infoLocation*/
    }

    override suspend fun getLocation2():LiveData<Location?> {
        startLocationUpdates()
        inf()
        return infoLocation
     /*   locRepo.startLocationUpdates()
        Log.e("locrepo",locRepo.toString())
       return locRepo._infoLocation*/
    }
    override suspend fun stopData(): Int{
        stopLocationUpdates()
        return 1
    }

override suspend fun saveLocationOnBD(lld:LocationLiveData): Int {
		//locRepo.startLocationUpdates()
	//	val mLocation = locRepo.lastLocation
    Log.e("insertLocation2",lld.toString())
    lld.value?.let {
        val res= LocationDbModel(
            it.time.toString(),
            it.latitude.toLong(),
            it.longitude.toLong(),
            1,
            it.accuracy.toInt(),
            it.speed.toInt()
        )
        val itemsCount= locationDao.insertLocation(res)
        Log.e("insertLocation",res.toString())
    }

    return  1
    }
}
