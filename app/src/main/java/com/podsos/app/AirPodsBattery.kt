package com.podsos.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings

data class AirPodsBatteryData(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null
)

class AirPodsBattery(
    private val onBatteryUpdate: (AirPodsBatteryData) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var lastPacket = ""

    private val callback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val record = result.scanRecord ?: return

            val bytes = record.getManufacturerSpecificData(0x004C)
                ?: return

            if (bytes.isEmpty()) return

            val packet = bytes.joinToString(" ") {
                "%02X".format(it.toInt() and 0xFF)
            }

            if (packet != lastPacket) {
                lastPacket = packet
                onStatusUpdate("Apple BLE data: $packet")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onStatusUpdate("Bluetooth scan failed: $errorCode")
        }
    }

    fun start() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            scanner = adapter?.bluetoothLeScanner

            if (scanner == null) {
                onStatusUpdate("BLE scanning unavailable")
                return
            }

            if (scanning) return

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(null, settings, callback)
            scanning = true

            onStatusUpdate("Scanning for Apple Bluetooth data...")

        } catch (e: SecurityException) {
            onStatusUpdate("Bluetooth scan permission required")
        }
    }

    fun stop() {
        try {
            if (scanning) {
                scanner?.stopScan(callback)
            }
        } catch (_: SecurityException) {
        }

        scanning = false
    }
}
