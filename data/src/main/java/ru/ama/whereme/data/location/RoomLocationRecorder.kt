package ru.ama.whereme.data.location

import android.location.Location
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.tracking.LocationRecorder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLocationRecorder @Inject constructor(
    private val database: AppDatabase,
    private val locationDao: LocationDao
) : LocationRecorder {
    private fun getDate(milliSeconds: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return formatter.format(calendar.time)
    }
    private fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    override suspend fun record(sample: LocationSample, settings: SettingsDomModel, previousRowId: Long?): Long =
        withContext(Dispatchers.IO) {
            val lTime = sample.timeMillis
            val location = Location("platform").apply {
                latitude = sample.latitude; longitude = sample.longitude
                accuracy = sample.accuracyMeters; speed = sample.speedMetersPerSecond
                time = sample.timeMillis; elapsedRealtimeNanos = sample.elapsedRealtimeNanos
            }
            database.withTransaction {
                val last = locationDao.getLastValue(getCurrentDate())
                val previous = last?.let {
                    Location(LOCATION_A).apply { latitude = it.latitude; longitude = it.longitude }
                }
                val gap = last?.let { lTime - (it.dateend ?: it.datestart) }
                val maxGap = (settings.timeOfWorkingWM.toLong().coerceAtLeast(1L) +
                    settings.timeOfWaitAccuracy.toLong().coerceAtLeast(1L)) * ONE_SECOND
                if (last != null && last._id == previousRowId && previous != null &&
                    gap != null && gap in 0..maxGap && previous.distanceTo(location) <= settings.minDist
                ) {
                    locationDao.updateInterval(
                        last._id, lTime, getDate(last.datestart) + " - " + getDate(lTime)
                    )
                    last._id
                } else {
                    locationDao.insertLocation(
                        LocationDbModel(
                            lTime.toString(), lTime, null, getDate(lTime), location.latitude,
                            location.longitude, ONE_INT, location.accuracy, location.speed, EMPTY_INT
                        )
                    )
                }
            }
        }

    private companion object {
        const val LOCATION_A = "lastValue"
        const val ONE_SECOND = 1000L
        const val EMPTY_INT = 0
        const val ONE_INT = 1
    }
}
