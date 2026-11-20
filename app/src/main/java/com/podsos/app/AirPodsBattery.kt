
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

            val record = result.scanRecord

            if (record == null) {
                onStatusUpdate(
                    "BLE callback received!\n" +
                    "No scan record\n" +
                    "RSSI: ${result.rssi} dBm\n" +
                    "Results: $resultCount"
                )
                return
            }

            val deviceName = record.deviceName ?: "Unknown device"

            val manufacturerData =
                record.getManufacturerSpecificData(0x004C)

            if (manufacturerData != null && manufacturerData.isNotEmpty()) {

                val packet = manufacturerData.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                if (packet != lastPacket) {
                    lastPacket = packet

                    onStatusUpdate(
                        "APPLE BLUETOOTH DATA\n" +
                        "Device: $deviceName\n" +
                        "RSSI: ${result.rssi} dBm\n" +
                        "Data: $packet"
                    )
                }

                return
            }

            val manufacturerIds = (0 until
                record.manufacturerSpecificData.size()).map {
                "0x%04X".format(
                    record.manufacturerSpecificData.keyAt(it)
                )
            }

            val services = record.serviceUuids?.joinToString {
                it.toString()
            } ?: "None"

            val deviceKey = "$deviceName|$manufacturerIds|$services"

            if (seenDevices.add(deviceKey)) {
                onStatusUpdate(
                    "BLE DEVICE DETECTED\n" +
                    "Name: $deviceName\n" +
                    "RSSI: ${result.rssi} dBm\n" +
                    "Manufacturer: ${manufacturerIds.ifEmpty { listOf("None") }}\n" +
                    "Services: $services\n" +
                    "Total results: $resultCount"
                )
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            onStatusUpdate(
                "BLE batch received: ${results.size} devices"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false

            onStatusUpdate(
                "BLUETOOTH SCAN FAILED\nError code: $errorCode"
            )
        }
    }

    fun start() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()

            if (adapter == null) {
                onStatusUpdate("Bluetooth adapter unavailable")
                return
            }

            if (!adapter.isEnabled) {
                onStatusUpdate("Bluetooth is turned off")
                return
            }

            scanner = adapter.bluetoothLeScanner

            if (scanner == null) {
                onStatusUpdate("BLE scanner unavailable")
                return
            }

            if (scanning) return

            resultCount = 0
            lastPacket = ""
            seenDevices.clear()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(null, settings, callback)

            scanning = true

            onStatusUpdate(
                "BLE scanner started\nWaiting for advertisements..."
            )

        } catch (e: SecurityException) {
            onStatusUpdate("Bluetooth scan permission denied")
        } catch (e: Exception) {
            onStatusUpdate(
                "Scanner error: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun stop() {
        try {
            if (scanning) {
                scanner?.stopScan(callback)
            }
        } catch (_: Exception) {
        }

        scanning = false
    }
}
