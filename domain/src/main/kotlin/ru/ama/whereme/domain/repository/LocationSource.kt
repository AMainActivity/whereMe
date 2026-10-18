package ru.ama.whereme.domain.repository

import ru.ama.whereme.domain.entity.LocationOutcome

interface LocationSource {
    /** Collect a fresh fix; cancellation must unregister every platform listener. */
    suspend fun locate(accuracyMeters: Int, timeoutMillis: Long): LocationOutcome
}
