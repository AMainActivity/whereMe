package ru.ama.whereme.domain.repository


interface NetworkStatus {
    fun isInternetConnected(): Boolean
}
