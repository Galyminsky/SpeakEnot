package com.galyaminsky.speakenot.domain

interface NetworkAvailability {
    fun isAvailable(): Boolean
}

object AlwaysOnline : NetworkAvailability {
    override fun isAvailable() = true
}
