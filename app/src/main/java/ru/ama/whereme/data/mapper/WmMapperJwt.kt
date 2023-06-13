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

    fun mapAllDtoToModel(dto: JsonDto) = JsonEntity(
        error = dto.error,
        message = dto.message
    )

}