package com.podsos.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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

    private val callback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            resultCount++

            val record = result.scanRecord

            if (record == null) {
                onStatusUpdate(
                    "BLE RESULT RECEIVED\n" +
                    "No scan record\n" +
                    "RSSI: ${result.rssi}\n" +
                    "Count: $resultCount"
                )
                return
            }

            val name = record.deviceName ?: "Unknown"

            val manufacturer = record.manufacturerSpecificData
            val manufacturerInfo = StringBuilder()

            for (i in 0 until manufacturer.size()) {
                val id = manufacturer.keyAt(i)
                val bytes = manufacturer.valueAt(i)

                val hex = bytes.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                manufacturerInfo.append(
                    "\nID: 0x%04X DATA: %s".format(id, hex)
                )
            }

            val serviceData = StringBuilder()

            record.serviceData?.forEach { (uuid, bytes) ->
                val hex = bytes.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                serviceData.append("\n$uuid: $hex")
            }

            onStatusUpdate(
                "BLE DEVICE DETECTED\n" +
                "Name: $name\n" +
                "RSSI: ${result.rssi} dBm\n" +
                "Manufacturer: ${manufacturerInfo.ifEmpty { "None" }}\n" +
                "Service data: ${serviceData.ifEmpty { "None" }}\n" +
                "Results: $resultCount"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onStatusUpdate("BLE SCAN FAILED: $errorCode")
        }
    }

    fun start() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()

            if (adapter == null || !adapter.isEnabled) {
                onStatusUpdate("Bluetooth unavailable or disabled")
                return
            }

            scanner = adapter.bluetoothLeScanner

            if (scanner == null) {
                onStatusUpdate("BLE scanner unavailable")
                return
            }

            if (scanning) return

            resultCount = 0

            val filters = emptyList<ScanFilter>()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0)
                .build()

            scanner?.startScan(callback)

            scanning = true

            onStatusUpdate(
                "BLE SCANNER ACTIVE\nWaiting for all advertisements..."
            )

        } catch (e: SecurityException) {
            onStatusUpdate("Bluetooth permission denied")
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
