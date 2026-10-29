package com.monuk7735.nope.remote.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedSignalDecoderTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun decodesAValueAcrossVariableWidthBlocks() {
        assertEquals(
            "Hello world",
            ImportedSignalDecoder.decodeBytes(
                bytes("825105ce9fbf7f5dd1c982"),
            ).toString(Charsets.UTF_8),
        )
    }

    @Test fun commonSignalMatchesReferenceCycles() {
        val param = bytes("00040ed8010010390016001500160040010004015a00ad00000106015a00560015")
        val actual = ImportedSignalDecoder.cycles(param, "0000bf00ff")
        assertEquals(
            listOf(
                346, 173, 22, 21, 22, 21, 22, 21, 22, 21, 22, 21,
                22, 21, 22, 21, 22, 21, 22, 64, 22, 64, 22, 64,
                22, 64, 22, 64, 22, 64, 22, 21, 22, 64, 22, 21,
                22, 21, 22, 21, 22, 21, 22, 21, 22, 21, 22, 21,
                22, 21, 22, 64, 22, 64, 22, 64, 22, 64, 22, 64,
                22, 64, 22, 64, 22, 64, 22, 1591, 346, 86, 21, 3700,
            ),
            actual,
        )
    }

    @Test fun anotherPeriodMatchesReferenceCycles() {
        val param = bytes("00040ecf010010860016001700160042010004015a00ad00000106015a00560015")
        assertEquals(
            listOf(
                346, 173, 22, 23, 22, 23, 22, 23, 22, 23, 22, 23,
                22, 23, 22, 23, 22, 23, 22, 66, 22, 66, 22, 23,
                22, 66, 22, 66, 22, 66, 22, 66, 22, 23, 22, 23,
                22, 66, 22, 23, 22, 66, 22, 23, 22, 23, 22, 66,
                22, 23, 22, 66, 22, 23, 22, 66, 22, 23, 22, 66,
                22, 66, 22, 23, 22, 66, 22, 1647, 346, 86, 21, 3777,
            ),
            ImportedSignalDecoder.cycles(param, "00007b4ab5"),
        )
    }

    @Test fun unknownAndMalformedFormatsAreRejected() {
        val param = bytes("00040ed8010010390016001500160040010004015a00ad00000106015a00560015")
        assertNull(ImportedSignalDecoder.cycles(param, "0100bf00ff"))
        assertNull(ImportedSignalDecoder.cycles(param, "not hex"))
        param[18] = 3
        assertFalse(ImportedSignalDecoder.supports(param))
        assertNull(ImportedSignalDecoder.cycles(param, "0000bf00ff"))
    }
}
