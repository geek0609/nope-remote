package com.monuk7735.nope.remote.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monuk7735.nope.remote.infrared.IRController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Search addresses and commands for a chosen protocol; this does not receive IR signals. */
@Composable
fun IrCodeScanner(
    protocol: ProtocolOption,
    device: Int?,
    subdevice: Int?,
    controller: IRController?,
    onCodeSelected: (Int, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf("0") }
    var end by remember { mutableStateOf("255") }
    var interval by remember { mutableStateOf("1500") }
    var addressStart by remember { mutableStateOf(device?.toString() ?: "0") }
    var addressEnd by remember { mutableStateOf(device?.toString() ?: "0") }
    var currentAddress by remember { mutableStateOf<Int?>(null) }
    var current by remember { mutableStateOf<Int?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(protocol, device, subdevice) {
        scanJob?.cancel()
        current = null
        currentAddress = null
        addressStart = device?.toString() ?: "0"
        addressEnd = device?.toString() ?: "0"
        running = false
    }

    fun transmit(command: Int) {
        val address = currentAddress ?: return
        if (running || controller == null || subdevice == null) return
        scanJob = scope.launch {
            running = true
            try {
                val pattern = generateProtocolPattern(protocol, address, subdevice, command)
                current = command
                withContext(Dispatchers.IO) { controller.transmit(pattern) }
                message =
                    "Sent address $address, command $command. Select it if the device responded."
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message ?: "Could not transmit the signal"
            } finally {
                running = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Find a command", style = MaterialTheme.typography.titleSmall)
        Text(
            "Point the phone at the device. Stop when it responds, select the last signal, " +
                "then add the button. This sends signals; it does not learn them.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = addressStart,
                onValueChange = { addressStart = it },
                label = { Text("First address") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = addressEnd,
                onValueChange = { addressEnd = it },
                label = { Text("Last address") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Search values are decimal. Some protocols use fewer address or command bits.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text("Start (decimal)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                label = { Text("End (decimal)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = interval,
            onValueChange = { interval = it },
            label = { Text("Delay between signals (ms)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (running) {
                        scanJob?.cancel()
                        message = "Stopped. Select the last signal below."
                    } else {
                        val first = start.toIntOrNull()
                        val last = end.toIntOrNull()
                        val firstAddress = addressStart.toIntOrNull()
                        val lastAddress = addressEnd.toIntOrNull()
                        val pause = interval.toLongOrNull()
                        if (controller == null || subdevice == null) {
                            message = "An IR blaster and valid subdevice are required."
                        } else if (first == null ||
                            last == null ||
                            first !in 0..255 ||
                            last !in first..255 ||
                            firstAddress == null ||
                            lastAddress == null ||
                            firstAddress !in 0..65535 ||
                            lastAddress !in firstAddress..65535 ||
                            (lastAddress - firstAddress + 1) * (last - first + 1) > 65536 ||
                            pause == null ||
                            pause !in 300..30000
                        ) {
                            message =
                                "Use addresses 0–65535, commands 0–255, at most 65536 signals and a delay of 300–30000 ms."
                        } else {
                            scanJob = scope.launch {
                                running = true
                                try {
                                    for (address in firstAddress..lastAddress) {
                                        for (command in first..last) {
                                            val pattern =
                                                generateProtocolPattern(
                                                    protocol,
                                                    address,
                                                    subdevice,
                                                    command,
                                                )
                                            currentAddress = address
                                            current = command
                                            message =
                                                "Sending address $address, command $command. Stop when the device responds."
                                            withContext(Dispatchers.IO) {
                                                controller.transmit(pattern)
                                            }
                                            if (address < lastAddress ||
                                                command < last
                                            ) {
                                                delay(pause)
                                            }
                                        }
                                    }
                                    message = "Scan complete. You can select the last signal below."
                                } catch (error: kotlinx.coroutines.CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    message = error.message ?: "Could not transmit the signal"
                                } finally {
                                    running = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (running) "Stop" else "Start scan") }
            OutlinedButton(
                onClick = { current?.let { transmit(it) } },
                enabled = !running && current != null,
                modifier = Modifier.weight(1f),
            ) { Text("Repeat") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { current?.let { transmit(it - 1) } },
                enabled = !running && current != null && current!! > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Previous") }
            OutlinedButton(
                onClick = { current?.let { transmit(it + 1) } },
                enabled = !running && current != null && current!! < 255,
                modifier = Modifier.weight(1f),
            ) { Text("Next") }
        }
        OutlinedButton(
            onClick = { onCodeSelected(currentAddress!!, current!!) },
            enabled = !running && currentAddress != null && current != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use last signal") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
