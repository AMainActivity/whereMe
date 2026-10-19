package ru.ama.whereme.tracking

import org.junit.Assert.*
import org.junit.Test

class TrackingAccessTest {
    private val ready = TrackingCapabilities(true, true, true, false, true, true)
    @Test fun foregroundDenialBlocksRecordingWithoutRequiringOtherPermissions() {
        assertEquals(TrackingRequirement.FOREGROUND_LOCATION, ready.copy(foreground = false).requirement(TrackingAction.MANUAL))
    }
    @Test fun manualPeriodicRecordingRequiresBackgroundAndAlarmAccess() {
        assertEquals(TrackingRequirement.BACKGROUND_LOCATION, ready.copy(background = false).requirement(TrackingAction.MANUAL))
        assertEquals(TrackingRequirement.EXACT_ALARM, ready.copy(exactAlarm = false).requirement(TrackingAction.MANUAL))
    }
    @Test fun backgroundAndAlarmAreSeparateScheduleRequirements() {
        assertEquals(TrackingRequirement.BACKGROUND_LOCATION, ready.copy(background = false, exactAlarm = false).requirement(TrackingAction.SCHEDULE))
        assertEquals(TrackingRequirement.EXACT_ALARM, ready.copy(exactAlarm = false).requirement(TrackingAction.SCHEDULE))
    }
    @Test fun approximateLocationWithAProviderIsEnough() {
        assertEquals(TrackingRequirement.READY, ready.requirement(TrackingAction.MANUAL))
    }
    @Test fun gpsOnlyDeviceCanAskForPreciseAccessWithoutClosingTheApp() {
        assertEquals(TrackingRequirement.PRECISE_LOCATION, ready.copy(provider = false, preciseWouldProvideLocation = true).requirement(TrackingAction.MANUAL))
    }
    @Test fun disabledLocationAndAbsentProviderAreDifferentProblems() {
        assertEquals(TrackingRequirement.LOCATION_ENABLED, ready.copy(locationEnabled = false, provider = false).requirement(TrackingAction.MANUAL))
        assertEquals(TrackingRequirement.PROVIDER, ready.copy(provider = false).requirement(TrackingAction.MANUAL))
    }
}
