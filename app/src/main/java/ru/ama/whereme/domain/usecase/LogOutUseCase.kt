package ru.ama.whereme.domain.usecase

import okhttp3.RequestBody
import ru.ama.whereme.domain.repository.WmRepository
import javax.inject.Inject

class LogOutUseCase @Inject constructor(
    private val repository: WmRepository
) {

    operator suspend fun invoke(request : RequestBody) = repository.logOut(request)
}
