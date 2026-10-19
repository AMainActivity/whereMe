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
        // Preserve the existing editor limits; storage accepts older positive configurations.
        fun minimum(field: MeasurementField): Int =
            when (field) {
                MeasurementField.DISTANCE -> 10
                MeasurementField.ACCURACY -> 50
                MeasurementField.WAIT -> 20
                MeasurementField.PERIOD -> 15
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
