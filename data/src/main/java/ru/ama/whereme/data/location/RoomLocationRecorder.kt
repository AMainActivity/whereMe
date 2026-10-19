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
import ru.ama.whereme.domain.usecase.DayRange
import java.text.SimpleDateFormat
import ru.ama.whereme.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLocationRecorder @Inject constructor(
    private val database: AppDatabase,
    private val locationDao: LocationDao,
    private val sessions: SessionRepository
) : LocationRecorder {
    private fun getDate(milliSeconds: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.ROOT)
        return formatter.format(java.util.Date(milliSeconds))
    }

    override suspend fun record(sample: LocationSample, settings: SettingsDomModel, previousRowId: Long?): Long =
        withContext(Dispatchers.IO) {
            val owner = try { sessions.current().profile.ownerKey }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: java.io.IOException) { null }
            val lTime = sample.timeMillis
            val location = Location("platform").apply {
                latitude = sample.latitude; longitude = sample.longitude
            }
            val day = DayRange.containing(lTime)
            database.withTransaction {
                val last = previousRowId?.let { locationDao.getLocation(it) }
                    ?.takeIf { it.ownerKey == owner && it.datestart >= day.start && it.datestart < day.end }
                val previous = last?.let {
                    Location("previous").apply { latitude = it.latitude; longitude = it.longitude }
                }
                val gap = last?.let { lTime - (it.dateend ?: it.datestart) }
                val maxGap = (settings.timeOfWorkingWM.toLong() +
                    settings.timeOfWaitAccuracy.toLong()) * ONE_SECOND
                if (last != null && previous != null &&
                    gap != null && gap in 0..maxGap && previous.distanceTo(location) <= settings.minDist
                ) {
                    locationDao.updateInterval(
                        last._id, lTime, getDate(last.datestart) + " - " + getDate(lTime)
                    )
                    last._id
                } else {
                    locationDao.insertLocation(
                        LocationDbModel(
                            datetime = lTime.toString(), datestart = lTime, info = getDate(lTime),
                            latitude = sample.latitude, longitude = sample.longitude,
                            sourceId = 1, accuracy = sample.accuracyMeters, velocity = sample.speedMetersPerSecond,
                            isWrite = 0, ownerKey = owner
                        )
                    )
                }
            }
        }

    private companion object {
        const val ONE_SECOND = 1000L
    }
}
