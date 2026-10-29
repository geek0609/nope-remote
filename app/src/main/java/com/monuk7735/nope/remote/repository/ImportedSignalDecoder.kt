package com.monuk7735.nope.remote.repository

import android.util.Base64

/** Decoder for one observed family of parameterized pulse-distance signals. */
internal object ImportedSignalDecoder {
    private const val KEY = 0x133a133L

    fun decodeBytes(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var pos = 0
        var width = 1
        while (pos < data.size) {
            val n = minOf(width, data.size - pos)
            var value = 0L
            for (i in 0 until n) value = (value shl 8) or (data[pos + i].toLong() and 255)
            value = (value - pos - KEY / (pos + 1)) xor
                ((KEY shr (9 - width)) + ((KEY shl width) and 0xffffffffL))
            for (i in 0 until n) out[pos + i] = (value ushr (8 * i)).toByte()
            pos += n
            width = if (width == 8) 1 else width + 1
        }
        return out
    }

    fun decodeText(s: String): String =
        decodeBytes(Base64.decode(s, Base64.DEFAULT)).toString(Charsets.UTF_8)

    fun supports(param: ByteArray): Boolean = param.size == 33 &&
        param[0] == 0.toByte() &&
        param[1] == 4.toByte() &&
        param[4] == 1.toByte() &&
        param[5] == 0.toByte() &&
        param[16] == 1.toByte() &&
        param[17] == 0.toByte() &&
        param[18] == 4.toByte() &&
        param[23] == 0.toByte() &&
        param[24] == 0.toByte() &&
        param[25] == 1.toByte() &&
        param[26] == 6.toByte()

    /** Returns carrier-cycle durations. Unknown payloads are rejected rather than guessed. */
    fun cycles(param: ByteArray, payloadText: String): List<Int>? {
        if (!supports(param) || !payloadText.matches(Regex("00[0-9a-fA-F]{8}"))) return null
        val data = payloadText.chunked(2).map { it.toInt(16) }
        fun word(i: Int) = ((param[i].toInt() and 255) shl 8) or (param[i + 1].toInt() and 255)
        val period = word(6)
        val cycles = mutableListOf(word(19), word(21))
        data.drop(1).forEach { byte ->
            for (bit in 0..7) {
                val one = (byte and (1 shl bit)) != 0
                cycles.add(word(if (one) 12 else 8))
                cycles.add(word(if (one) 14 else 10))
            }
        }
        val finalMark = word(8)
        val remaining = period - cycles.sum() - finalMark
        val repeatGap = period - word(27) - word(29) - word(31)
        cycles.addAll(listOf(finalMark, remaining, word(27), word(29), word(31), repeatGap))
        return cycles.takeIf { it.all { duration -> duration > 0 } }
    }
}
