package ru.ama.whereme.data.repository

import android.app.Application
import android.location.Location
import com.google.android.gms.location.sample.foregroundlocation.data.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import ru.ama.whereme.data.database.TestInfoDao
import ru.ama.whereme.data.database.TestQuestionsDao
import ru.ama.whereme.data.mapper.TestMapper
import ru.ama.whereme.domain.entity.TestInfo
import ru.ama.whereme.domain.entity.TestQuestion
import ru.ama.whereme.domain.repository.TestsRepository
import javax.inject.Inject


class TestsRepositoryImpl @Inject constructor(
    private val mapper: TestMapper,
    private val testQuestionsDao: TestQuestionsDao,
    private val testInfoDao: TestInfoDao,
    private val application: Application,
    private val locRepo: LocationRepository
) : TestsRepository {



    override fun getQuestionsInfoList(testId: Int,limit:Int): List<TestQuestion>{
      ///  var rl:MutableList<TestQuestion> = mutableListOf<TestQuestion>()
        val list=testQuestionsDao.getQuestionListByTestId(testId,limit)
     ///   Log.e("getQuestionsrl1","${testId} ${limit} ${list.toString()}")
		val llist2=list.map {
            mapper.mapDbModelToEntity(it)
        }

     ///   for (l in list)
     ///   {
      ///      rl.add(mapper.mapDbModelToEntity(l))
      ///  }
      ///  Log.e("getQuestionsrl","${testId} ${limit} ${rl.toString()}")
		
		
		
        return llist2

    }
   /*  fun getQuestionsInfoList2(): LiveData<List<TestQuestion>> {
        return Transformations.map(testQuestionsDao.getQuestionList()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }*/

    override fun getTestInfo(): List<TestInfo> {
      /*  Log.e("getTestInfo1",testInfoDao.toString())
        Log.e("getTestInfo",testInfoDao.getTestInfo().toString())
       // if(testInfoDao.getTestInfo().value!=null)
        var rl:MutableList<TestInfo> = mutableListOf<TestInfo>()
        for (l in testInfoDao.getTestInfo())
        {
            rl.add(mapper.mapDataDbModelToEntity(l))
        }*/
		
			val rl=(testInfoDao.getTestInfo()).map  {mapper.mapDataDbModelToEntity(it)}
		
        return rl
       // return  mapper.mapDataDbModelToEntity(testInfoDao.getTestInfo(testId))
    }

    override suspend fun loadData():List<Int>  {
        var listOfItems:MutableList<Int> = mutableListOf<Int>()

			  return listOfItems
		
 
     
    }

    override fun getLocation(): StateFlow<Location?> {
       return locRepo.lastLocation
    }


}
