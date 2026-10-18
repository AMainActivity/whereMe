package ru.ama.whereme.data.repository

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ru.ama.whereme.domain.repository.NetworkStatus
import javax.inject.Inject

class AndroidNetworkStatus @Inject constructor(private val application: Application) : NetworkStatus {
    override fun isInternetConnected(): Boolean {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
