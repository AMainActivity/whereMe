package ru.ama.whereme.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import ru.ama.whereme.data.network.WmApiService
import javax.inject.Singleton
import ru.ama.whereme.data.network.model.DatasToJson
import javax.inject.Inject

@Singleton
class LocationBatchUploader @Inject constructor(
    private val store: PendingLocationStore,
    private val api: WmApiService
) {
    private val mutex = Mutex()

    /** Local data stays pending on every failure; cancellation belongs to the calling service. */
    suspend fun uploadPending(timeoutMillis: Long = 30_000L) = mutex.withLock {
        val batch = store.snapshot() ?: return@withLock
        if (batch.payload.isEmpty() || !store.isCurrent(batch)) return@withLock
        val request = Gson().toJson(DatasToJson(batch.session.tokenJwt, batch.payload))
            .toRequestBody("application/json".toMediaType())
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
        if (accepted && store.isCurrent(batch)) {
            // Each original row is attempted once. Zero updated rows is a valid stale-snapshot result.
            store.acknowledge(batch)
        }
    }
}
