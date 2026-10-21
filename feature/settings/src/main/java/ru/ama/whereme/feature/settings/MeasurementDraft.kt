package ru.ama.whereme.feature.settings

import ru.ama.whereme.domain.entity.SettingsDomModel

enum class MeasurementField {
    DISTANCE,
    ACCURACY,
    WAIT,
    PERIOD,
}

@kotlinx.serialization.Serializable
data class MeasurementDraft(
    val distance: String,
    val accuracy: String,
    val wait: String,
    val period: String,
) {
    fun value(field: MeasurementField): String =
        when (field) {
            MeasurementField.DISTANCE -> distance
            MeasurementField.ACCURACY -> accuracy
            MeasurementField.WAIT -> wait
            MeasurementField.PERIOD -> period
        }

    fun edit(field: MeasurementField, text: String): MeasurementDraft =
        when (field) {
            MeasurementField.DISTANCE -> copy(distance = text)
            MeasurementField.ACCURACY -> copy(accuracy = text)
            MeasurementField.WAIT -> copy(wait = text)
            MeasurementField.PERIOD -> copy(period = text)
        }

    fun isValid(field: MeasurementField): Boolean =
        value(field).toIntOrNull()?.let {
            it >= minimum(field)
        } == true

    val isValid: Boolean
        get() = MeasurementField.entries.all(::isValid)

    fun applyTo(settings: SettingsDomModel): SettingsDomModel {
        require(isValid)
        return settings.copy(
            minDist = distance.toInt(),
            accuracy = accuracy.toInt(),
            timeOfWaitAccuracy = wait.toInt(),
            timeOfWorkingWM = period.toInt(),
        )
    }

    companion object {
        private const val MIN_DISTANCE_METERS = 10
        private const val MIN_ACCURACY_METERS = 50
        private const val MIN_WAIT_SECONDS = 20
        private const val MIN_PERIOD_SECONDS = 15
        // Preserve the existing editor limits; storage accepts older positive configurations.
        fun minimum(field: MeasurementField): Int =
            when (field) {
                MeasurementField.DISTANCE -> MIN_DISTANCE_METERS
                MeasurementField.ACCURACY -> MIN_ACCURACY_METERS
                MeasurementField.WAIT -> MIN_WAIT_SECONDS
                MeasurementField.PERIOD -> MIN_PERIOD_SECONDS
            }

        fun from(settings: SettingsDomModel) =
            MeasurementDraft(
                settings.minDist.toString(),
                settings.accuracy.toString(),
                settings.timeOfWaitAccuracy.toString(),
                settings.timeOfWorkingWM.toString(),
            )
    }
}
