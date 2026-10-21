package ru.ama.whereme.tracking

import java.time.Instant
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ama.whereme.R
import ru.ama.whereme.domain.state.LoadState
import ru.ama.whereme.domain.tracking.TrackingFailure
import ru.ama.whereme.domain.tracking.TrackingMode
import ru.ama.whereme.domain.tracking.TrackingState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TrackingNotificationTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val mode = TrackingMode.MANUAL
    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()
    private val fix = LoadState.Ready(Instant.ofEpochMilli(1_792_501_542_000L)) // 20 October 2026, 13:05:42 UTC.

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    private fun render(state: TrackingState, time: LoadState<Instant?> = fix): Notification {
        TrackingNotification(context).render(state, time)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.single().notification
    }

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun savedFixTimeSurvivesNewMeasurementUploadFailureAndNotificationRecreation() {
        val waiting = render(TrackingState.Waiting(mode, noFix = false, syncFailed = false))
        // Robolectric's ICU shadow returns the skeleton yMdHms without separators.
        // Check the actual fix's date/time fields; native localized punctuation is a device check.
        assertEquals(context.getString(R.string.tracking_last_fix, "2026102013542"), waiting.text())
        assertEquals(waiting.text(), render(TrackingState.Collecting(mode, 5000, 4000)).text())
        assertEquals(waiting.text(), render(TrackingState.Saving(mode)).text())
        assertEquals(waiting.text(), render(TrackingState.Uploading(mode, noFix = false)).text())
        assertEquals(waiting.text(), render(TrackingState.Waiting(mode, false, true)).text())
        assertEquals(0, waiting.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(waiting.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertFalse(waiting.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertNotNull(waiting.contentIntent)
        assertEquals(context.getString(R.string.tracking_stop), waiting.actions.single().title)
    }

    @Test
    fun missingHistoryAndFailedReadAreNotInventedFixTimes() {
        val starting = TrackingState.Starting(mode)
        assertEquals(
            context.getString(R.string.tracking_recording),
            TrackingNotification(context).initial().text(),
        )
        assertEquals(
            context.getString(R.string.tracking_recording),
            render(starting, LoadState.Loading).text(),
        )
        assertEquals(
            context.getString(R.string.tracking_no_recorded_fix),
            render(starting, LoadState.Ready(null)).text(),
        )
        assertEquals(
            context.getString(R.string.tracking_history_failed),
            render(starting, LoadState.Failed).text(),
        )
    }

    @Test
    fun failedMeasurementKeepsPreviousFixAndReportsProblem() {
        val previous = render(TrackingState.Waiting(mode, false, false)).text()
        val noFix = render(TrackingState.Waiting(mode, true, false))
        assertEquals("$previous\n${context.getString(R.string.tracking_fix_failed)}", noFix.text())
        assertEquals(
            noFix.text(),
            noFix.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
        val storage = render(TrackingState.Waiting(mode, true, false, TrackingFailure.STORAGE))
        assertEquals(
            "$previous\n${context.getString(R.string.tracking_storage_failed)}",
            storage.text(),
        )
    }
    @Test
    @Config(shadows = [LocalizedDateFormat::class])
    fun icuLiteralHashPatternDoesNotCrashNotificationRendering() {
        Locale.setDefault(Locale.forLanguageTag("tok"))
        val notification = render(TrackingState.Waiting(mode, false, false))
        assertEquals(context.getString(R.string.tracking_last_fix, "#2026)#10)#20 #13:05:42"), notification.text())
    }

    @Test
    @Config(shadows = [LocalizedDateFormat::class])
    fun twelveHourNotificationUsesTheCurrentTimeZoneAndAmPm() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Yekaterinburg"))
        val notification = render(TrackingState.Waiting(mode, false, false))
        assertEquals(context.getString(R.string.tracking_last_fix, "10/20/2026, 6:05:42 PM"), notification.text())
    }

    // Fixed real ICU patterns bypass Robolectric's skeleton-only shadow without adding a library.
    @org.robolectric.annotation.Implements(android.text.format.DateFormat::class)
    class LocalizedDateFormat {
        companion object {
            @JvmStatic
            @org.robolectric.annotation.Implementation
            fun getBestDateTimePattern(locale: Locale, skeleton: String): String =
                if (locale.language == "tok") "#y)#M)#d #HH:mm:ss"
                else if ('H' in skeleton) "M/d/y, HH:mm:ss" else "M/d/y, h:mm:ss a"
        }
    }

}
