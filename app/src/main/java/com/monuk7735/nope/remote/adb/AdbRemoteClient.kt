package com.monuk7735.nope.remote.adb

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbCrypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/** Classic ADB-over-TCP client for a TV with debugging enabled. */
object AdbRemoteClient {
    private val base64 = AdbBase64 { data -> Base64.encodeToString(data, Base64.NO_WRAP) }
    private val keyLock = Any()
    private val keyCodes = mapOf(
        "up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23,
        "back" to 4, "home" to 3, "volume_up" to 24, "volume_down" to 25,
        "mute" to 164, "play_pause" to 85, "power" to 26, "menu" to 82,
    )

    fun execute(context: Context, host: String, port: Int, action: String) {
        require(host.isNotBlank() && port in 1..65535) { "Enter a valid TV address and port" }
        val command = commandForAction(action)
        val crypto = getCrypto(context)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            val response = AdbWire(socket, crypto).execute("$command; echo __NOPE_EXIT_\$?")
            validateResponse(action, response)
        }
    }

    internal fun commandForAction(action: String): String {
        if (action == "ping") return "echo nope_adb_ready"
        val code = keyCodes[action] ?: throw IllegalArgumentException("Unsupported key")
        return "input keyevent $code"
    }

    internal fun validateResponse(action: String, response: String) {
        val lines = response.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.lastOrNull() != "__NOPE_EXIT_0") {
            throw IOException("TV did not complete the ADB command")
        }
        if (action == "ping" && "nope_adb_ready" !in lines.dropLast(1)) {
            throw IOException("The TV did not respond to ADB")
        }
    }

    private fun getCrypto(context: Context): AdbCrypto = synchronized(keyLock) {
        val file = AtomicFile(context.noBackupFilesDir.resolve("adb-remote-key"))
        val pair = if (file.baseFile.exists()) {
            DataInputStream(file.openRead()).use { input ->
                fun readKey(): ByteArray {
                    val size = input.readInt()
                    require(size in 1..8192) { "Invalid saved ADB key" }
                    return ByteArray(size).also(input::readFully)
                }
                val privateKey = readKey()
                val publicKey = readKey()
                val factory = KeyFactory.getInstance("RSA")
                KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicKey)),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateKey)),
                )
            }
        } else {
            val generated = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()
            val raw = file.startWrite()
            try {
                val output = DataOutputStream(raw)
                val privateKey = generated.private.encoded
                val publicKey = generated.public.encoded
                output.writeInt(privateKey.size)
                output.write(privateKey)
                output.writeInt(publicKey.size)
                output.write(publicKey)
                output.flush()
                file.finishWrite(raw)
            } catch (error: Exception) {
                file.failWrite(raw)
                throw error
            }
            generated
        }
        AdbCrypto.loadAdbKeyPair(base64, pair)
    }
}
