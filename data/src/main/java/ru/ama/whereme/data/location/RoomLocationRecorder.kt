package ru.ama.whereme.data.location

import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import android.location.Location
import androidx.room.withTransaction
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.tracking.LocationRecorder
import ru.ama.whereme.domain.usecase.DayRange

@Singleton
class RoomLocationRecorder @Inject constructor(
    private val database: AppDatabase,
    private val locationDao: LocationDao,
    private val sessions: SessionRepository
) : LocationRecorder {
    private fun getDate(time: Instant): String =
        dateTimeFormat.withZone(ZoneId.systemDefault()).format(time)

    override suspend fun record(sample: LocationSample, settings: SettingsDomModel, previousRowId: Long?): Long =
        withContext(Dispatchers.IO) {
            val owner = try { sessions.current().profile.ownerKey }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: java.io.IOException) { null }
            val lTime = sample.time.toEpochMilli()
            val location = Location(CURRENT_LOCATION_PROVIDER).apply {
                latitude = sample.latitude; longitude = sample.longitude
            }
            val day = DayRange.containing(sample.time)
            database.withTransaction {
                val last = previousRowId?.let { locationDao.getLocation(it) }
                    ?.takeIf { it.ownerKey == owner && it.datestart >= day.start.toEpochMilli() && it.datestart < day.end.toEpochMilli() }
                val previous = last?.let {
                    Location(PREVIOUS_LOCATION_PROVIDER).apply { latitude = it.latitude; longitude = it.longitude }
                }
                val gap = last?.let { lTime - (it.dateend ?: it.datestart) }
                val maxGap = TimeUnit.SECONDS.toMillis(settings.timeOfWorkingWM.toLong() +
                    settings.timeOfWaitAccuracy.toLong())
                if (last != null && previous != null &&
                    gap != null && gap in 0..maxGap && previous.distanceTo(location) <= settings.minDist
                ) {
                    locationDao.updateInterval(
                        last._id, lTime, getDate(Instant.ofEpochMilli(last.datestart)) + " - " + getDate(sample.time)
                    )
                    last._id
                } else {
                    locationDao.insertLocation(
                        LocationDbModel(
                            datetime = lTime.toString(), datestart = lTime, info = getDate(sample.time),
                            latitude = sample.latitude, longitude = sample.longitude,
                            sourceId = LocationDbModel.SOURCE_DEVICE, accuracy = sample.accuracyMeters, velocity = sample.speedMetersPerSecond,
                            isWrite = LocationDbModel.UPLOAD_PENDING, ownerKey = owner
                        )
                    )
                }
            }
        }

    private companion object {
        val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.ROOT)
        const val CURRENT_LOCATION_PROVIDER = "platform"
        const val PREVIOUS_LOCATION_PROVIDER = "previous"
    }
}
