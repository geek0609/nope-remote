package com.monuk7735.nope.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monuk7735.nope.remote.composables.AppBar
import com.monuk7735.nope.remote.ui.theme.NopeRemoteTheme
import com.monuk7735.nope.remote.ui.theme.rememberThemeSettings
import com.monuk7735.nope.tvbridge.BridgeProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CompanionRemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme = rememberThemeSettings()
            NopeRemoteTheme(
                useDarkTheme = theme.useDarkTheme,
                useDynamicColors = theme.useDynamicColors,
            ) {
                CompanionRemoteScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionRemoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }
    var host by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Install and open Nope TV Companion on the TV box.") }
    var text by rememberSaveable { mutableStateOf("") }

    fun send(action: String, value: String = "") {
        val address = host.trim()
        val pairingCode = code.trim()
        scope.launch {
            try {
                val response = mutex.withLock {
                    withContext(Dispatchers.IO) {
                        val key = BridgeProtocol.key(pairingCode)
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(address, BridgeProtocol.PORT), 3000)
                            socket.soTimeout = 5000
                            val request = JSONObject().put(
                                "action",
                                action,
                            ).put("text", value).toString()
                            val nonce = BridgeProtocol.write(
                                DataOutputStream(socket.getOutputStream()),
                                key,
                                request,
                                BridgeProtocol.requestContext,
                            )
                            val (_, reply) = BridgeProtocol.read(
                                DataInputStream(socket.getInputStream()),
                                key,
                                BridgeProtocol.responseContext(nonce),
                            )
                            JSONObject(reply)
                        }
                    }
                }
                if (action == "ping") connected = response.optBoolean("ok")
                if (action == "copy" && response.optBoolean("ok")) {
                    text = response.optString("text")
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE,
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TV clipboard", text))
                    message = "Copied TV text to the phone clipboard"
                } else {
                    message = response.optString("message", "Done")
                }
            } catch (error: Exception) {
                if (action == "ping") connected = false
                message = error.message ?: "Could not reach the TV companion"
            }
        }
    }

    Scaffold(topBar = { AppBar(title = "TV Companion", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!connected) {
                Text(
                    "Enable the companion accessibility service on the TV and enter its address and pairing code.",
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("TV IP address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    send("ping")
                }, enabled = host.isNotBlank() && code.isNotBlank()) {
                    Text("Connect")
                }
            } else {
                Text("Connected to $host", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        send("back")
                    }, modifier = Modifier.weight(1f)) { Text("Back") }
                    OutlinedButton(onClick = {
                        send("home")
                    }, modifier = Modifier.weight(1f)) { Text("Home") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { send("up") }, modifier = Modifier.weight(1f)) { Text("↑") }
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { send("left") }, modifier = Modifier.weight(1f)) { Text("←") }
                    Button(onClick = { send("ok") }, modifier = Modifier.weight(1f)) { Text("OK") }
                    Button(onClick = {
                        send("right")
                    }, modifier = Modifier.weight(1f)) { Text("→") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { send("down") }, modifier = Modifier.weight(1f)) { Text("↓") }
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        send("volume_down")
                    }, modifier = Modifier.weight(1f)) { Text("Vol −") }
                    OutlinedButton(onClick = {
                        send("mute")
                    }, modifier = Modifier.weight(1f)) { Text("Mute") }
                    OutlinedButton(onClick = {
                        send("volume_up")
                    }, modifier = Modifier.weight(1f)) { Text("Vol +") }
                }
                OutlinedButton(onClick = {
                    send("play_pause")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Play / Pause")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text for TV") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE,
                            ) as ClipboardManager
                            text =
                                clipboard.primaryClip?.getItemAt(
                                    0,
                                )?.coerceToText(context)?.toString().orEmpty()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Phone clipboard") }
                    Button(onClick = {
                        send("paste", text)
                    }, modifier = Modifier.weight(1f)) { Text("Paste on TV") }
                }
                OutlinedButton(onClick = { send("copy") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy selection from TV")
                }
                OutlinedButton(onClick = {
                    connected = false
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change TV")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
