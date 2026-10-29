package com.monuk7735.nope.tvbridge

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** One authenticated, encrypted request and response per connection. */
object BridgeProtocol {
    const val PORT = 42784
    private const val MAX_PACKET = 65536
    private val random = SecureRandom()

    fun newSecret(): String = ByteArray(16).also(random::nextBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun key(secret: String): ByteArray {
        require(secret.matches(Regex("[0-9a-fA-F]{32}"))) {
            "Pairing code must contain 32 hexadecimal characters"
        }
        return ByteArray(16) { i -> secret.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun write(
        output: DataOutputStream,
        key: ByteArray,
        text: String,
        context: ByteArray,
    ): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context)
        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        require(ciphertext.size + nonce.size <= MAX_PACKET) { "Message too long" }
        output.writeInt(ciphertext.size + nonce.size)
        output.write(nonce)
        output.write(ciphertext)
        output.flush()
        return nonce
    }

    fun read(input: DataInputStream, key: ByteArray, context: ByteArray): Pair<ByteArray, String> {
        val length = input.readInt()
        require(length in 28..MAX_PACKET) { "Invalid message length" }
        val packet = ByteArray(length)
        input.readFully(packet)
        val nonce = packet.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context)
        val text = cipher.doFinal(packet, 12, packet.size - 12).toString(Charsets.UTF_8)
        return nonce to text
    }

    val requestContext: ByteArray get() = "nope-tv-request-v1".toByteArray(Charsets.UTF_8)
    fun responseContext(requestNonce: ByteArray): ByteArray =
        "nope-tv-response-v1".toByteArray(Charsets.UTF_8) + requestNonce
}
