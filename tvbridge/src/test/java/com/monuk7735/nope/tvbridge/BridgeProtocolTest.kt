package com.monuk7735.nope.tvbridge

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BridgeProtocolTest {
    @Test fun encryptedMessageRoundTrip() {
        val key = BridgeProtocol.key(BridgeProtocol.newSecret())
        val buffer = ByteArrayOutputStream()
        BridgeProtocol.write(
            DataOutputStream(buffer),
            key,
            "text 🌍",
            BridgeProtocol.requestContext,
        )
        val decoded = BridgeProtocol.read(
            DataInputStream(ByteArrayInputStream(buffer.toByteArray())),
            key,
            BridgeProtocol.requestContext,
        )
        assertEquals("text 🌍", decoded.second)
    }

    @Test(expected = Exception::class)
    fun changedCiphertextIsRejected() {
        val key = BridgeProtocol.key(BridgeProtocol.newSecret())
        val buffer = ByteArrayOutputStream()
        BridgeProtocol.write(DataOutputStream(buffer), key, "home", BridgeProtocol.requestContext)
        val packet = buffer.toByteArray()
        packet[packet.lastIndex] = (packet.last().toInt() xor 1).toByte()
        BridgeProtocol.read(
            DataInputStream(ByteArrayInputStream(packet)),
            key,
            BridgeProtocol.requestContext,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCodeIsRejected() {
        BridgeProtocol.key("1234")
    }

    @Test fun serverHandlesAuthenticatedRequestAndRejectsReplay() {
        val key = BridgeProtocol.key(BridgeProtocol.newSecret())
        val calls = AtomicInteger()
        val server = BridgeServer(key, 0) { request ->
            calls.incrementAndGet()
            "reply:$request"
        }
        try {
            server.start()
            val deadline = System.currentTimeMillis() + 3000
            while (server.boundPort == null &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(10)
            }
            assertNotNull("Listener should start", server.boundPort)
            val port = server.boundPort!!
            val packet = ByteArrayOutputStream()
            val nonce = BridgeProtocol.write(
                DataOutputStream(packet),
                key,
                "home",
                BridgeProtocol.requestContext,
            )
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write(packet.toByteArray())
                val reply = BridgeProtocol.read(
                    DataInputStream(socket.getInputStream()),
                    key,
                    BridgeProtocol.responseContext(nonce),
                )
                assertEquals("reply:home", reply.second)
            }
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write(packet.toByteArray())
                assertEquals(-1, socket.getInputStream().read())
            }
            assertEquals(1, calls.get())
        } finally {
            server.close()
        }
    }
}
