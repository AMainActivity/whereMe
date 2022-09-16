package ru.ama.ottest.data.mapper

import ru.ama.whereme.data.network.model.JsonJwtDto
import ru.ama.whereme.domain.entity.JsonJwt
import javax.inject.Inject


class WmMapperJwt @Inject constructor() {

    fun mapDtoToModel(dto: JsonJwtDto) = JsonJwt(
        error = dto.error,
        message = dto.message,
        tokenJwt = dto.tokenJwt,
        posId = dto.posId,
        famId = dto.famId,
        name = dto.name,
        isActivate = dto.isActivate,
        respCode = dto.respCode,
        respError = dto.respError,
        respIsSuccess = dto.respIsSuccess
    )

    fun mapModelToDto(model: JsonJwt) = JsonJwtDto(
        error = model.error,
        message = model.message,
        posId = model.posId,
        tokenJwt = model.tokenJwt,
        famId = model.famId,
        name = model.name,
        isActivate = model.isActivate,
        respCode = model.respCode,
        respError = model.respError,
        respIsSuccess = model.respIsSuccess
    )
   
}
