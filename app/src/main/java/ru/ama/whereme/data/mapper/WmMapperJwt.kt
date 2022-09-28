package ru.ama.ottest.data.mapper

import ru.ama.whereme.data.network.model.JsonDto
import ru.ama.whereme.data.network.model.JsonJwtDto
import ru.ama.whereme.domain.entity.JsonEntity
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
        url = dto.url,
        isActivate = dto.isActivate
    )

    fun mapModelToDto(model: JsonJwt) = JsonJwtDto(
        error = model.error,
        message = model.message,
        posId = model.posId,
        tokenJwt = model.tokenJwt,
        famId = model.famId,
        name = model.name,
        url = model.url,
        isActivate = model.isActivate
    )

    fun mapAllDtoToModel(dto: JsonDto) = JsonEntity(
        error = dto.error,
        message = dto.message
    )

    fun mapAllModelToDto(model: JsonEntity) = JsonDto(
        error = model.error,
        message = model.message
    )

}
