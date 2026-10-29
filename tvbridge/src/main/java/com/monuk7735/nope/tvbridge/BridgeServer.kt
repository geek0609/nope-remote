package com.monuk7735.nope.tvbridge

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** Runs the local companion listener until closed. Handler is called on the listener thread. */
class BridgeServer(
    private val key: ByteArray,
    private val port: Int = BridgeProtocol.PORT,
    private val handler: (String) -> String,
) : AutoCloseable {
    private val active = AtomicBoolean(false)

    @Volatile private var listener: ServerSocket? = null

    @Volatile private var clientSocket: Socket? = null

    @Volatile var boundPort: Int? = null
        private set

    fun start() {
        check(active.compareAndSet(false, true))
        Thread {
            val seen = HashSet<String>()
            try {
                val socket = ServerSocket(port)
                listener = socket
                if (!active.get()) return@Thread
                boundPort = socket.localPort
                socket.use {
                    while (active.get()) {
                        it.accept().use { client ->
                            clientSocket = client
                            client.soTimeout = 3000
                            try {
                                val (nonce, request) = BridgeProtocol.read(
                                    DataInputStream(client.getInputStream()),
                                    key,
                                    BridgeProtocol.requestContext,
                                )
                                val nonceId = nonce.joinToString("") { byte ->
                                    "%02x".format(
                                        byte.toInt() and 0xff,
                                    )
                                }
                                check(seen.size < 100000 && seen.add(nonceId)) {
                                    "Repeated request"
                                }
                                check(active.get()) { "Server stopped" }
                                val reply = handler(request)
                                BridgeProtocol.write(
                                    DataOutputStream(client.getOutputStream()),
                                    key,
                                    reply,
                                    BridgeProtocol.responseContext(nonce),
                                )
                            } catch (_: Exception) {
                                // Invalid, unauthenticated or repeated requests receive no response.
                            } finally {
                                clientSocket = null
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Bind failures and listener shutdown both leave the listener unavailable.
            } finally {
                listener?.close()
                listener = null
                boundPort = null
                active.set(false)
            }
        }.apply {
            name = "nope-tv-server"
            start()
        }
    }

    override fun close() {
        active.set(false)
        clientSocket?.close()
        listener?.close()
    }
}
