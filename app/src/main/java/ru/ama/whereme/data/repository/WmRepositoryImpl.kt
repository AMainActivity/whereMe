package ru.ama.whereme.data.repository

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.location.KalmanLatLong
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.workers.GetLocationDataWorker
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.repository.WmRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class WmRepositoryImpl @Inject constructor(
    private val mapper: WmMapper,
    private val locationDao: LocationDao,
    private val application: Application,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationScope private val externalScope: CoroutineScope
) : WmRepository {

    val kalmanLatLong = KalmanLatLong(1f)
    private val callback = Callback()

    private val _infoLocation = MutableLiveData<Location?>()
    val infoLocation: LiveData<Location?>
        get() = _infoLocation
		
    private val _isEnathAccuracy = MutableLiveData<Boolean>()
    val isEnathAccuracy: LiveData<Boolean>
        get() = _isEnathAccuracy



fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
}



  override fun runWorker(timeInterval:Long) {
        val workManager = WorkManager.getInstance(application)
        workManager.enqueueUniqueWork(
            GetLocationDataWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            GetLocationDataWorker.makeRequest(timeInterval)
        )
      Log.e("runWorker",""+timeInterval)
    }

/*
  override fun loadData() {
        val workManager = WorkManager.getInstance(application)
        workManager.enqueueUniqueWork(
            RefreshDataWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            RefreshDataWorker.makeRequest()
        )
    }
*/


    override suspend fun GetLocationsFromBd():LiveData<List<LocationDb>>  {
      return Transformations.map(locationDao.getLocations()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }      
    }
	
    override suspend fun loadData():List<Int>  {
        var listOfItems:MutableList<Int> = mutableListOf<Int>()

			  return listOfItems
		
 
     
    }
    @SuppressLint("MissingPermission") // Only called when holding location permission.
    fun startLocationUpdates() {
        _isEnathAccuracy.value=false
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 4000 // 10 seconds
            fastestInterval=2000
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
       /* if (ActivityCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
*/
//            Log.e("getflpc",fusedLocationProviderClient.lastLocation.result.toString())
         //   _infoLocation.value =fusedLocationProviderClient.lastLocation.result
       /*     return
        }*/
        Log.e("getLocation00",fusedLocationProviderClient.toString())
    }

  /*  suspend fun inf()
    {
        Log.e("insertLocation2",infoLocation.value.toString())
        infoLocation.value?.let {
            val res= LocationDbModel(
                it.time.toString(),
                it.latitude,
                it.longitude,
                1,
                it.accuracy,
                it.speed
            )
            val itemsCount= locationDao.insertLocation(res)
            Log.e("insertLocation",res.toString())
        }
    }*/


    fun updateValueDb(id:Int, newInfo:String):Int
    {
        return locationDao.updateLocationById(id, newInfo)
    }
    fun getLastValueFromDb():LocationDbModel
    {
        val d=locationDao.getLastValue()
        return locationDao.getLastValue()
    }

    fun getDate(milliSeconds: Long): String? {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeInMillis(milliSeconds)
        return formatter.format(calendar.getTime())
    }


    private inner class Callback: LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            _infoLocation.value = result.lastLocation
          //  Log.e("getflpc2",result.lastLocation.toString())
          //  if( result.lastLocation== null)

//externalScope.launch {  }
/*
 executor=Executors.newSingleThreadExecutor()
 
   executor.execute {
            locationDao.addLocations(myLocationEntities)
        }
*/
val ll=infoLocation.value
            ll?.let {
                kalmanLatLong.Process(ll.latitude,
                    ll.longitude,
                    ll.accuracy,
                    ll.time
                )
            }

			//kalmanLatLong.get_lat()
		//	kalmanLatLong.get_lng()
			Log.e("kalmanLatLong",ll?.latitude.toString() +"#"+ll?.longitude.toString())
			Log.e("kalmanLatLong2",kalmanLatLong.get_lat().toString() +"#"+kalmanLatLong.get_lng().toString())
			
            ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO)  {//Log.e("insertLocation2",infoLocation.value.toString())
                val lastDbValue=getLastValueFromDb()

                result.lastLocation.let {
                    if (lastDbValue!=null)
                    {
                        val locA=Location("lastValue")
                        locA.latitude=lastDbValue.latitude
                        locA.longitude=lastDbValue.longitude
                        val locB=Location("newValue")
                        locB.latitude=it.latitude
                        locB.longitude=it.longitude
                        val dist=locA.distanceTo(locB)
                        Log.e("distanceLastNew",dist.toString())
                        if (dist>50) {
                            val res = LocationDbModel(
                                it.time.toString(),
                                getDate(it.time),
                                it.latitude,
                                it.longitude,
                                1,
                                it.accuracy,
                                it.speed
                            )
                            val itemsCount = locationDao.insertLocation(res)
_isEnathAccuracy.postValue(true)
                            Log.e("insertLocation", res.toString())
                        }
                        else
                        {
                            updateValueDb(lastDbValue._id.toInt(),getDate(lastDbValue.datetime.toLong())+"#"+getDate(it.time))
_isEnathAccuracy.postValue(true)
                        }
                    }
                    else
                    {
                        val res = LocationDbModel(
                            it.time.toString(),
                            getDate(it.time),
                            it.latitude,
                            it.longitude,
                            1,
                            it.accuracy,
                            it.speed
                        )
                        val itemsCount = locationDao.insertLocation(res)
_isEnathAccuracy.postValue(true)

                        Log.e("insertLocationNull", res.toString())
                    }



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
    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation():LiveData<Location?> {

        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
            //val d=fusedLocationProviderClient.lastLocation.result
            _infoLocation.value = it
            Log.e("getflpc",it.toString())
        }
            return infoLocation
        /*   locRepo.startLocationUpdates()
           Log.e("locrepo",locRepo.toString())
          return locRepo._infoLocation*/
    }


    //@SuppressLint("MissingPermission")
    override suspend fun getLocation2():LiveData<Location?> {
       Log.e("getLocation2","getLocation2")
        startLocationUpdates()
     //   inf()
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
            it.time.toString(),
            it.latitude,
            it.longitude,
            1,
            it.accuracy,
            it.speed
        )
        val itemsCount= locationDao.insertLocation(res)
        Log.e("insertLocation",res.toString())
    }

    return  1
    }
}



/*

public class KalmanLatLong {
    private final float MinAccuracy = 1;

    private float Q_metres_per_second;    
    private long TimeStamp_milliseconds;
    private double lat;
    private double lng;
    private float variance; // P matrix.  Negative means object uninitialised.  NB: units irrelevant, as long as same units used throughout

    public KalmanLatLong(float Q_metres_per_second) { this.Q_metres_per_second = Q_metres_per_second; variance = -1; }

    public long get_TimeStamp() { return TimeStamp_milliseconds; }
    public double get_lat() { return lat; }
    public double get_lng() { return lng; }
    public float get_accuracy() { return (float)Math.sqrt(variance); }

    public void SetState(double lat, double lng, float accuracy, long TimeStamp_milliseconds) {
        this.lat=lat; this.lng=lng; variance = accuracy * accuracy; this.TimeStamp_milliseconds=TimeStamp_milliseconds;
    }

    /// <summary>
    /// Kalman filter processing for lattitude and longitude
    /// </summary>
    /// <param name="lat_measurement_degrees">new measurement of lattidude</param>
    /// <param name="lng_measurement">new measurement of longitude</param>
    /// <param name="accuracy">measurement of 1 standard deviation error in metres</param>
    /// <param name="TimeStamp_milliseconds">time of measurement</param>
    /// <returns>new state</returns>
    public void Process(double lat_measurement, double lng_measurement, float accuracy, long TimeStamp_milliseconds) {
        if (accuracy < MinAccuracy) accuracy = MinAccuracy;
        if (variance < 0) {
            // if variance < 0, object is unitialised, so initialise with current values
            this.TimeStamp_milliseconds = TimeStamp_milliseconds;
            lat=lat_measurement; lng = lng_measurement; variance = accuracy*accuracy; 
        } else {
            // else apply Kalman filter methodology

            long TimeInc_milliseconds = TimeStamp_milliseconds - this.TimeStamp_milliseconds;
            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position increases
                variance += TimeInc_milliseconds * Q_metres_per_second * Q_metres_per_second / 1000;
                this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION
            }

            // Kalman gain matrix K = Covarariance * Inverse(Covariance + MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that variance has different units to lat and lng
            float K = variance / (variance + accuracy * accuracy);
            // apply K
            lat += K * (lat_measurement - lat);
            lng += K * (lng_measurement - lng);
            // new Covarariance  matrix is (IdentityMatrix - K) * Covarariance 
            variance = (1 - K) * variance;
        }
    }
}
*/