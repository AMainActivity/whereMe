package ru.ama.whereme.feature.settings

import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.state.LoadState

class SettingsReducerTest {
    private val original = MeasurementDraft.from(SettingsDomModel.DEFAULT)

    @Test
    fun completedSaveDoesNotReplaceNewerTyping() {
        val submitted = original.copy(period = "150")
        val newer = submitted.copy(period = "200")
        val state = SettingsState(draft = newer, saving = true)
        val result = reduceSettings(state, SettingsResult.Applied(submitted, submitted))
        assertEquals(newer, result.draft)
        assertFalse(result.saving)
    }

    @Test
    fun loadingFailureDoesNotEraseUnsavedInput() {
        val edited = original.copy(period = "")
        val result =
            reduceSettings(SettingsState(draft = edited), SettingsResult.Loaded(LoadState.Failed))
        assertEquals(edited, result.draft)
        assertEquals(LoadState.Failed, result.settings)
    }

    @Test
    fun scheduleEmissionDoesNotResetTheMeasurementDraft() {
        val edited = original.copy(accuracy = "100")
        val result =
            reduceSettings(
                SettingsState(draft = edited),
                SettingsResult.Loaded(
                    LoadState.Ready(SettingsDomModel.DEFAULT.copy(isEnable = true))
                ),
            )
        assertEquals(edited, result.draft)
    }
}
