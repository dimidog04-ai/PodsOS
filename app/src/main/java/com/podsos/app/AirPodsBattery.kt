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
    private var resultCount = 0
    private var lastPacket = ""
    private val seenDevices = mutableSetOf<String>()

    private val callback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            resultCount++

            val record = result.scanRecord ?: return

            val bytes = record.getManufacturerSpecificData(0x004C)

            if (bytes != null && bytes.isNotEmpty()) {
                val packet = bytes.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                if (packet != lastPacket) {
                    lastPacket = packet
                    onStatusUpdate("Apple BLE data: $packet")
                }

                return
            }

            val name = record.deviceName ?: "Unknown device"

            val manufacturerIds = (0 until
                record.manufacturerSpecificData.size()).map {
                "0x%04X".format(
                    record.manufacturerSpecificData.keyAt(it)
                )
            }

            val services = record.serviceUuids?.joinToString {
                it.toString()
            } ?: "None"

            val deviceKey = "$name|$manufacturerIds|$services"

            if (seenDevices.add(deviceKey)) {
                onStatusUpdate(
                    "BLE detected: $name\n" +
                    "RSSI: ${result.rssi} dBm\n" +
                    "Manufacturer IDs: ${manufacturerIds.ifEmpty { listOf("None") }}\n" +
                    "Services: $services\n" +
                    "Total scan results: $resultCount"
                )
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

            resultCount = 0
            lastPacket = ""
            seenDevices.clear()

            onStatusUpdate("Scanning for nearby Bluetooth devices...")

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
