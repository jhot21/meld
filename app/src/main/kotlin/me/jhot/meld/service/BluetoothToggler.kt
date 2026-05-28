package me.jhot.meld.service

interface BluetoothToggler {
    suspend fun setBluetooth(enable: Boolean): Boolean
}
