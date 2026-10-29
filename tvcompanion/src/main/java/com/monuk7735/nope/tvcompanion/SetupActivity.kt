package com.monuk7735.nope.tvcompanion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.monuk7735.nope.tvbridge.BridgeProtocol
import java.net.Inet4Address
import java.net.NetworkInterface

@Synchronized
internal fun secret(activity: android.content.Context): String {
    val prefs = activity.getSharedPreferences("pairing", 0)
    return prefs.getString("secret", null) ?: BridgeProtocol.newSecret().also {
        prefs.edit().putString("secret", it).commit()
    }
}

@Synchronized
internal fun rotateSecret(context: android.content.Context): String =
    BridgeProtocol.newSecret().also {
        context.getSharedPreferences("pairing", 0).edit().putString("secret", it).commit()
    }

class SetupActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pairingDetails: TextView
    private val refresh = object : Runnable {
        override fun run() {
            val status = if (RemoteAccessibilityService.listening) {
                "Service is ready"
            } else {
                "Service is not running"
            }
            pairingDetails.text = "Pairing code: ${secret(this@SetupActivity)}\n$status"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().filter {
                    !it.isLoopbackAddress &&
                        it.isSiteLocalAddress
                }
                .joinToString("\n") { it.hostAddress.orEmpty() }
        }.getOrDefault("")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 32, 64, 32)
        }
        fun line(value: String, size: Float) = TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, 8, 0, 8)
            layout.addView(this)
        }
        line("Nope TV Companion", 28f)
        line(
            "Enable the Nope TV Companion accessibility service. Keep both devices on the same network.",
            18f,
        )
        line(
            "TV address: ${addresses.ifBlank {
                "Connect to Wi-Fi, then reopen this screen"
            }}",
            18f,
        )
        pairingDetails = TextView(this).apply {
            textSize = 18f
            setPadding(0, 8, 0, 8)
            layout.addView(this)
        }
        line("Enter the address and pairing code in Nope Remote on your phone.", 18f)
        layout.addView(
            Button(this).apply {
                text = "Open accessibility settings"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
        )
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        refresh.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
