// SPDX-License-Identifier: GPL-3.0-or-later
package com.podsos.app.aap

// Protocol field layouts adapted from LibrePods (GPL-3.0-or-later).
// Original project: https://github.com/kavishdevar/librepods

enum class BatteryChargeStatus {
    CHARGING,
    NOT_CHARGING,
    DISCONNECTED,
    OPTIMIZED_CHARGING
}

data class BatteryComponentState(
    val level: Int,
    val status: BatteryChargeStatus
)

data class BatteryState(
    val left: BatteryComponentState,
    val right: BatteryComponentState,
    val case: BatteryComponentState
)

data class EarDetectionState(
    val leftInEar: Boolean,
    val rightInEar: Boolean
)

sealed interface AapEvent {
    data class Battery(val state: BatteryState) : AapEvent
    data class EarDetection(val state: EarDetectionState) : AapEvent
    data object Unrecognized : AapEvent
}

object AapPacketDecoder {

    private const val OPCODE_BATTERY_INFO = 0x04
    private const val OPCODE_EAR_DETECTION = 0x06
    private const val COMPONENT_LEFT = 0x02

    fun decode(packet: ByteArray): AapEvent {
        if (packet.size < 6 ||
            packet[0] != 0x04.toByte() ||
            packet[1] != 0x00.toByte() ||
            packet[2] != 0x04.toByte() ||
            packet[3] != 0x00.toByte()
        ) {
            return AapEvent.Unrecognized
        }

        return when (packet[4].toInt() and 0xFF) {
            OPCODE_BATTERY_INFO -> decodeBattery(packet)
            OPCODE_EAR_DETECTION -> decodeEarDetection(packet)
            else -> AapEvent.Unrecognized
        }
    }

    private fun decodeBattery(packet: ByteArray): AapEvent {
        if (packet.size != 22) return AapEvent.Unrecognized

        val first = componentState(packet, 7, 9, 10)
        val second = componentState(packet, 12, 14, 15)
        val case = componentState(packet, 17, 19, 20)

        val left: BatteryComponentState
        val right: BatteryComponentState

        when {
            first.first == COMPONENT_LEFT && second.first == 0x04 -> {
                left = first.second
                right = second.second
            }
            first.first == 0x04 && second.first == COMPONENT_LEFT -> {
                left = second.second
                right = first.second
            }
            else -> return AapEvent.Unrecognized
        }

        if (left.level !in 0..100 ||
            right.level !in 0..100 ||
            case.second.level !in 0..100
        ) {
            return AapEvent.Unrecognized
        }

        return AapEvent.Battery(
            BatteryState(
                left = left,
                right = right,
                case = case.second
            )
        )
    }

    private fun componentState(
        packet: ByteArray,
        componentIndex: Int,
        levelIndex: Int,
        statusIndex: Int
    ): Pair<Int, BatteryComponentState> {

        val component = packet[componentIndex].toInt() and 0xFF
        val level = packet[levelIndex].toInt() and 0xFF

        val status = when (packet[statusIndex].toInt() and 0xFF) {
            1 -> BatteryChargeStatus.CHARGING
            2 -> BatteryChargeStatus.NOT_CHARGING
            5 -> BatteryChargeStatus.OPTIMIZED_CHARGING
            else -> BatteryChargeStatus.DISCONNECTED
        }

        return component to BatteryComponentState(level, status)
    }

    private fun decodeEarDetection(packet: ByteArray): AapEvent {
        if (packet.size != 8) return AapEvent.Unrecognized

        val rightInEar = (packet[6].toInt() and 0xFF) == 0
        val leftInEar = (packet[7].toInt() and 0xFF) == 0

        return AapEvent.EarDetection(
            EarDetectionState(
                leftInEar = leftInEar,
                rightInEar = rightInEar
            )
        )
    }
}
