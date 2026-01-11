package com.resq.resq_sos_mientrung_android.uwb

/**
 * Stub classes for UWB API to allow compilation
 * These will be replaced by actual Android UWB API at runtime on supported devices
 */

// Stub UwbAddress - in production, this would be android.hardware.uwb.UwbAddress
data class UwbAddressStub(val address: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UwbAddressStub) return false
        return address.contentEquals(other.address)
    }

    override fun hashCode(): Int {
        return address.contentHashCode()
    }
}
