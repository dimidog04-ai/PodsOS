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
    val case: Int? = null,
    val leftCharging: Boolean? = null,
    val rightCharging: Boolean? = null,
    val caseCharging: Boolean? = null
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
            var decodedBattery: AirPodsBatteryData? = null
            android.util.Log.d("PodsOS_BLE", "========== BLE PACKET ==========")
            android.util.Log.d("PodsOS_BLE", "Device: ${record.deviceName}")
            android.util.Log.d("PodsOS_BLE", "RSSI: ${result.rssi} dBm")
            for (i in 0 until manufacturer.size()) {
                val id = manufacturer.keyAt(i)
                val bytes = manufacturer.valueAt(i)
                val hex = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
                android.util.Log.d("PodsOS_BLE", "Manufacturer ID: 0x%04X | RAW: %s".format(id, hex))
            }

            for (i in 0 until manufacturer.size()) {
                val id = manufacturer.keyAt(i)
                val bytes = manufacturer.valueAt(i)

                val hex = bytes.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                manufacturerInfo.append(
                    "\nID: 0x%04X DATA: %s".format(id, hex)
                )

                // Run the user's AirPods decoder on Apple manufacturer data.
                if (id == 0x004C) {
                    decodedBattery = parseAirPodsPayload(bytes)
                }
            }

            val serviceData = StringBuilder()

            record.serviceData?.forEach { (uuid, bytes) ->
                val hex = bytes.joinToString(" ") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                serviceData.append("\n$uuid: $hex")
            }

            decodedBattery?.let {
                onBatteryUpdate(it)
            }

            onStatusUpdate(
                "BLE DEVICE DETECTED\n" +
                (decodedBattery?.let {
                    "AIRPODS BATTERY DECODED\n" +
                    "Left: ${it.left}% | Right: ${it.right}% | Case: ${it.case}%\n" +
                    "Charging: L=${it.leftCharging}, R=${it.rightCharging}, Case=${it.caseCharging}\n"
                } ?: "") +
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


    // Kotlin translation of the user's original JavaScript decoder.
    private fun parseAirPodsPayload(bytes: ByteArray): AirPodsBatteryData? {
        if (bytes.size < 11 || (bytes[0].toInt() and 0xFF) != 0x07) {
            return null
        }

        val status = bytes[3].toInt() and 0xFF
        val podsBatteryByte = bytes[5].toInt() and 0xFF
        val chargeAndCaseByte = bytes[6].toInt() and 0xFF

        val isFlipped = (status and 0x02) != 0
        val highNibble = (podsBatteryByte shr 4) and 0x0F
        val lowNibble = podsBatteryByte and 0x0F

        val rawRight = if (isFlipped) lowNibble else highNibble
        val rawLeft = if (isFlipped) highNibble else lowNibble
        val rawCase = chargeAndCaseByte and 0x0F

        fun toPercent(value: Int): Int? =
            if (value in 0..10) value * 10 else null

        return AirPodsBatteryData(
            left = toPercent(rawLeft),
            right = toPercent(rawRight),
            case = toPercent(rawCase),
            leftCharging = (chargeAndCaseByte and 0x01) != 0,
            rightCharging = (chargeAndCaseByte and 0x02) != 0,
            caseCharging = (chargeAndCaseByte and 0x04) != 0
        )
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

            scanner?.startScan(filters, settings, callback)

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
