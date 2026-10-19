package ru.ama.whereme.data.network.model

import kotlinx.serialization.Serializable

/** The legacy backend uses the same `kod` field for a login code and a session token. */
@Serializable
data class CredentialRequest(val kod: String)
