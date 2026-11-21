package com.podsos.app.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AapPacketDecoderTest {

    @Test
    fun decodesCapturedBatteryPacket() {
        val packet = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x04, 0x00,
            0x03, 0x02, 0x01, 0x60, 0x02,
            0x01, 0x04, 0x01, 0x5F, 0x02,
            0x01, 0x08, 0x01, 0x00, 0x04, 0x01
        )

        val result = AapPacketDecoder.decode(packet)

        assertTrue(result is AapEvent.Battery)

        val state = (result as AapEvent.Battery).state

        assertEquals(96, state.left.level)
        assertEquals(95, state.right.level)
        assertEquals(0, state.case.level)

        assertEquals(
            BatteryChargeStatus.NOT_CHARGING,
            state.left.status
        )
        assertEquals(
            BatteryChargeStatus.NOT_CHARGING,
            state.right.status
        )
        assertEquals(
            BatteryChargeStatus.DISCONNECTED,
            state.case.status
        )
    }

    @Test
    fun decodesCapturedEarDetectionPacket() {
        val packet = byteArrayOf(
            0x04, 0x00, 0x04, 0x00,
            0x06, 0x00, 0x01, 0x01
        )

        val result = AapPacketDecoder.decode(packet)

        assertEquals(
            AapEvent.EarDetection(
                EarDetectionState(
                    leftInEar = false,
                    rightInEar = false
                )
            ),
            result
        )
    }

    @Test
    fun rejectsInvalidComponentIds() {
        val packet = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x04, 0x00,
            0x03, 0x02, 0x01, 0x60, 0x02,
            0x01, 0x08, 0x01, 0x5F, 0x02,
            0x01, 0x08, 0x01, 0x00, 0x04, 0x01
        )

        assertEquals(
            AapEvent.Unrecognized,
            AapPacketDecoder.decode(packet)
        )
    }

    @Test
    fun rejectsBatteryPercentageAbove100() {
        val packet = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x04, 0x00,
            0x03, 0x02, 0x01, 0x65, 0x02,
            0x01, 0x04, 0x01, 0x5F, 0x02,
            0x01, 0x08, 0x01, 0x00, 0x04, 0x01
        )

        assertEquals(
            AapEvent.Unrecognized,
            AapPacketDecoder.decode(packet)
        )
    }
}
