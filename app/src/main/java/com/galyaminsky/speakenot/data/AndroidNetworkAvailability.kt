package com.galyaminsky.speakenot.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.galyaminsky.speakenot.domain.NetworkAvailability

class AndroidNetworkAvailability(context: Context) : NetworkAvailability {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isAvailable(): Boolean = try {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: SecurityException) {
        false
    }
}
