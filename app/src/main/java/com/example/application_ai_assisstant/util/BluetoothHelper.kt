package com.example.application_ai_assisstant.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

class BluetoothHelper(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevicesNames(): List<String> {
        return bluetoothAdapter?.bondedDevices?.map { it.name } ?: emptyList()
    }
}
