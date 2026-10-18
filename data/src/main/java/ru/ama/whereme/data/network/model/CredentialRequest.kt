package ru.ama.whereme.data.network.model

/** The legacy backend uses the same `kod` field for a login code and a session token. */
data class CredentialRequest(val kod: String)
