package ru.ama.whereme.data.repository

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.sample.foregroundlocation.data.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.data.mapper.TestMapper
import ru.ama.whereme.domain.entity.TestInfo
import ru.ama.whereme.domain.entity.TestQuestion
import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject


class TestsRepositoryImpl @Inject constructor(
    private val mapper: TestMapper,
    private val locationDao: LocationDao,
    private val application: Application,
    private val locRepo: LocationRepository
) : TestsRepository {



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

    override suspend fun getLocation(): LocationLiveData {
         val locationData = LocationLiveData(application)

        return locationData
     /*   locRepo.startLocationUpdates()
        Log.e("locrepo",locRepo.toString())
       return locRepo._infoLocation*/
    }

    override suspend fun getLocation2():LiveData<Location?> {
        locRepo.startLocationUpdates()
        return locRepo.infoLocation
     /*   locRepo.startLocationUpdates()
        Log.e("locrepo",locRepo.toString())
       return locRepo._infoLocation*/
    }
    override suspend fun stopData(): Int{
        locRepo.stopLocationUpdates()
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
