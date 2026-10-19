package ru.ama.whereme.feature.settings

import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.SettingsDomModel

class MeasurementDraftTest {
    @Test
    fun incompleteInputRemainsADraft() {
        val saved = SettingsDomModel.DEFAULT
        val draft = MeasurementDraft.from(saved).edit(MeasurementField.PERIOD, "")
        assertFalse(draft.isValid)
        assertEquals(100, saved.timeOfWorkingWM)
    }

    @Test
    fun applyChangesOnlyMeasurementFields() {
        val saved = SettingsDomModel.DEFAULT.copy(start = "11:00", isEnable = true)
        val draft = MeasurementDraft.from(saved).edit(MeasurementField.PERIOD, "150")
        assertEquals(saved.copy(timeOfWorkingWM = 150), draft.applyTo(saved))
    }

    @Test
    fun invalidAndOverflowingNumbersCannotBeApplied() {
        for (value in listOf("", "abc", "-1", "0", "999999999999999999999")) {
            val draft =
                MeasurementDraft.from(SettingsDomModel.DEFAULT).edit(MeasurementField.PERIOD, value)
            assertFalse(draft.isValid)
            try {
                draft.applyTo(SettingsDomModel.DEFAULT)
                fail("Invalid input")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun existingEditorLimitsArePreserved() {
        val draft = MeasurementDraft("10", "50", "20", "15")
        assertTrue(draft.isValid)
        assertEquals(10, draft.applyTo(SettingsDomModel.DEFAULT).minDist)
    }
}
