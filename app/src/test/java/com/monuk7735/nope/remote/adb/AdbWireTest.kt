package com.monuk7735.nope.remote.adb

import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbCrypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbWireTest {
    private data class Packet(val cmd: Int, val first: Int, val second: Int, val data: ByteArray)
    private fun code(s: String) =
        ByteBuffer.wrap(s.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int
    private fun read(input: DataInputStream): Packet {
        val header = ByteArray(24).also(input::readFully)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val first = buffer.int
        val second = buffer.int
        val size = buffer.int
        buffer.int // checksum
        buffer.int // magic
        return Packet(command, first, second, ByteArray(size).also(input::readFully))
    }

    private fun send(
        output: DataOutputStream,
        command: String,
        first: Int,
        second: Int,
        data: ByteArray = byteArrayOf(),
    ) {
        val value = code(command)
        output.write(
            ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value).putInt(first).putInt(second).putInt(data.size)
                .putInt(data.sumOf { it.toInt() and 0xff }).putInt(value xor -1).array(),
        )
        output.write(data)
        output.flush()
    }

    @Test fun signsAuthenticationAndReadsTheWholeShellResponse() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val crypto = AdbCrypto.loadAdbKeyPair(
            AdbBase64 {
                Base64.getEncoder().encodeToString(it)
            },
            pair,
        )
        val worker = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0).use { listener ->
                val server = worker.submit {
                    listener.accept().use { client ->
                        client.soTimeout = 5000
                        val input = DataInputStream(client.getInputStream())
                        val output = DataOutputStream(client.getOutputStream())
                        assertEquals(code("CNXN"), read(input).cmd)
                        val token = ByteArray(20) { it.toByte() }
                        send(output, "AUTH", 1, 0, token)
                        val signature = read(input)
                        assertEquals(code("AUTH"), signature.cmd)
                        assertEquals(2, signature.first)
                        val verifier = Cipher.getInstance("RSA/ECB/NoPadding")
                        verifier.init(Cipher.DECRYPT_MODE, pair.public)
                        assertArrayEquals(
                            AdbCrypto.SIGNATURE_PADDING + token,
                            verifier.doFinal(signature.data),
                        )
                        send(output, "AUTH", 1, 0, token)
                        val publicKey = read(input)
                        assertEquals(3, publicKey.first)
                        assertTrue(publicKey.data.toString(Charsets.UTF_8).endsWith("\u0000"))
                        send(output, "CNXN", 0x01000000, 4096, "device::\u0000".toByteArray())
                        val open = read(input)
                        assertEquals(code("OPEN"), open.cmd)
                        assertEquals("shell:echo test\u0000", open.data.toString(Charsets.UTF_8))
                        send(output, "OKAY", 7, open.first)
                        send(output, "WRTE", 7, open.first, "hello".toByteArray())
                        assertEquals(code("OKAY"), read(input).cmd)
                        send(output, "WRTE", 7, open.first, " world".toByteArray())
                        assertEquals(code("OKAY"), read(input).cmd)
                        send(output, "CLSE", 7, open.first)
                    }
                }
                Socket("127.0.0.1", listener.localPort).use { client ->
                    assertEquals("hello world", AdbWire(client, crypto).execute("echo test"))
                }
                server.get(5, TimeUnit.SECONDS)
            }
        } finally {
            worker.shutdownNow()
        }
    }

    @Test(expected = IOException::class)
    fun rejectsOversizedPacketsBeforeAllocating() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val crypto = AdbCrypto.loadAdbKeyPair(
            AdbBase64 {
                Base64.getEncoder().encodeToString(it)
            },
            pair,
        )
        val worker = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0).use { listener ->
                val server = worker.submit {
                    listener.accept().use { client ->
                        read(DataInputStream(client.getInputStream()))
                        val value = code("CNXN")
                        client.getOutputStream().write(
                            ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(value).putInt(0).putInt(0).putInt(Int.MAX_VALUE)
                                .putInt(0).putInt(value xor -1).array(),
                        )
                    }
                }
                try {
                    Socket("127.0.0.1", listener.localPort).use {
                        AdbWire(it, crypto).execute("echo test")
                    }
                } finally {
                    server.get(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            worker.shutdownNow()
        }
    }
}
