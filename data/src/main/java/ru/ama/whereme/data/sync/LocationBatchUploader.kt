package ru.ama.whereme.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ama.whereme.data.network.WmApiService
import ru.ama.whereme.domain.tracking.PendingLocationUploader
import ru.ama.whereme.domain.tracking.UploadResult
import javax.inject.Singleton
import ru.ama.whereme.data.network.model.DatasToJson
import ru.ama.whereme.data.network.model.LocationDto
import javax.inject.Inject

@Singleton
class LocationBatchUploader @Inject constructor(
    private val store: PendingLocationStore,
    private val api: WmApiService
) : PendingLocationUploader {
    private val mutex = Mutex()

    /** Local data stays pending on every failure; cancellation belongs to the calling service. */
    override suspend fun uploadPending() = uploadPending(timeoutMillis = 30_000L)

    suspend fun uploadPending(timeoutMillis: Long): UploadResult = withContext(Dispatchers.IO) {
        mutex.withLock { uploadBatch(timeoutMillis) }
    }

    private suspend fun uploadBatch(timeoutMillis: Long): UploadResult {
        val batch = store.snapshot() ?: return UploadResult.SKIPPED
        if (batch.payload.isEmpty() || !store.isCurrent(batch)) return UploadResult.SKIPPED
        val request = DatasToJson(batch.session.profile.tokenJwt, batch.payload.map(LocationDto::from))
        val accepted = withTimeoutOrNull(timeoutMillis) {
            val response = try {
                api.writeLocDatas(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withTimeoutOrNull false
            }
            try {
                val body = response.body()
                val message: String? = body?.message
                // Preserve the legacy positive-response contract; server ACK semantics are still unverified.
                response.isSuccessful && body != null && !body.error && !message.isNullOrEmpty()
            } finally {
                response.errorBody()?.close()
            }
        } ?: false
        currentCoroutineContext().ensureActive()
        if (!accepted) return UploadResult.FAILED
        if (!store.isCurrent(batch)) return UploadResult.SKIPPED
        // Each original row is attempted once. Zero updated rows is a valid stale-snapshot result.
        store.acknowledge(batch)
        return UploadResult.SENT
    }
}
