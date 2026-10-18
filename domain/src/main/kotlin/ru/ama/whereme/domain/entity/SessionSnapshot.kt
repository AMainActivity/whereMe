package ru.ama.whereme.domain.entity

/** A new generation distinguishes separate logins even when their credentials are equal. */
data class SessionSnapshot(val profile: SettingsUserInfoDomModel, val generation: String)
