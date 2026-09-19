package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isNetworkConnected = MutableStateFlow(checkInitialConnectivity())
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    // Debug / developer simulation toggle
    private val _simulateOffline = MutableStateFlow(false)
    val simulateOffline: StateFlow<Boolean> = _simulateOffline.asStateFlow()

    private val _isOffline = MutableStateFlow(!_isNetworkConnected.value || _simulateOffline.value)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkConnected.value = true
                    updateOfflineState()
                }

                override fun onLost(network: Network) {
                    _isNetworkConnected.value = checkInitialConnectivity()
                    updateOfflineState()
                }
            })
        } catch (e: Exception) {
            // In test/restricted environments, fallback to initial state
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        return try {
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun setSimulateOffline(enabled: Boolean) {
        _simulateOffline.value = enabled
        updateOfflineState()
    }

    private fun updateOfflineState() {
        _isOffline.value = _simulateOffline.value || !_isNetworkConnected.value
    }
}
