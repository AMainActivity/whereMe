package ru.ama.whereme.domain.repository

import ru.ama.whereme.domain.entity.*

interface SessionRepository {
    fun getWmUserInfoSetings(): SettingsUserInfoDomModel
    fun setWmUserInfoSetings(set: SettingsUserInfoDomModel)
}
