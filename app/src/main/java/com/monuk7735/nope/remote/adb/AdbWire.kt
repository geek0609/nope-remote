package com.monuk7735.nope.remote.adb

import com.tananaev.adblib.AdbCrypto
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A single classic ADB transport and shell stream, with bounded packet reads. */
internal class AdbWire(private val socket: Socket, private val crypto: AdbCrypto) {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    private data class Packet(
        val command: Int,
        val first: Int,
        val second: Int,
        val data: ByteArray,
    )

    fun execute(command: String): String {
        socket.tcpNoDelay = true
        socket.soTimeout = 65000
        write(CNXN, 0x01000000, MAX_PACKET, "host::\u0000".toByteArray())
        var authAttempts = 0
        while (true) {
            val packet = read()
            when (packet.command) {
                CNXN -> break
                AUTH -> {
                    if (packet.first != 1 || packet.data.size != 20) {
                        throw IOException("Invalid ADB authentication challenge")
                    }
                    when (authAttempts++) {
                        0 -> write(AUTH, 2, 0, crypto.signAdbTokenPayload(packet.data))
                        1 -> write(AUTH, 3, 0, crypto.adbPublicKeyPayload)
                        else -> throw IOException("The TV rejected the ADB key")
                    }
                }
                else -> throw IOException("Unsupported ADB connection; use classic ADB over TCP")
            }
        }

        socket.soTimeout = 10000
        val localId = 1
        write(OPEN, localId, 0, "shell:$command\u0000".toByteArray(Charsets.UTF_8))
        var remoteId: Int? = null
        val response = ByteArrayOutputStream()
        while (true) {
            val packet = read()
            if (packet.second != localId) throw IOException("Invalid ADB stream identifier")
            when (packet.command) {
                OKAY -> {
                    if (packet.first == 0 ||
                        remoteId != null
                    ) {
                        throw IOException("Invalid ADB stream")
                    }
                    remoteId = packet.first
                }
                WRTE -> {
                    if (remoteId == null ||
                        packet.first != remoteId
                    ) {
                        throw IOException("Invalid ADB stream")
                    }
                    if (response.size() + packet.data.size >
                        65536
                    ) {
                        throw IOException("ADB response too large")
                    }
                    response.write(packet.data)
                    write(OKAY, localId, remoteId)
                }
                CLSE -> {
                    if (remoteId == null ||
                        packet.first != remoteId
                    ) {
                        throw IOException("ADB command was rejected")
                    }
                    return response.toString("UTF-8")
                }
                else -> throw IOException("Unexpected ADB stream message")
            }
        }
    }

    private fun write(command: Int, first: Int, second: Int, data: ByteArray = byteArrayOf()) {
        require(data.size <= MAX_PACKET)
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(first).putInt(second).putInt(data.size)
            .putInt(checksum(data)).putInt(command xor -1).array()
        output.write(header)
        output.write(data)
        output.flush()
    }

    private fun read(): Packet {
        val bytes = ByteArray(24)
        input.readFully(bytes)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val first = header.int
        val second = header.int
        val size = header.int
        val expectedChecksum = header.int
        val magic = header.int
        if (magic != command xor -1 ||
            size !in 0..MAX_PACKET
        ) {
            throw IOException("Invalid ADB packet")
        }
        val data = ByteArray(size)
        input.readFully(data)
        // Recent peers may omit checksums; older peers send the byte sum.
        if (expectedChecksum != 0 && expectedChecksum != checksum(data)) {
            throw IOException("Invalid ADB checksum")
        }
        return Packet(command, first, second, data)
    }

    private fun checksum(data: ByteArray): Int = data.sumOf { it.toInt() and 0xff }

    private companion object {
        const val MAX_PACKET = 256 * 1024
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257
        const val CLSE = 0x45534c43
    }
}
