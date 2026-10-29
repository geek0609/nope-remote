package com.monuk7735.nope.remote.infrared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Sends one complete signal before reporting it as a candidate, even when stopped mid-send. */
object IrCommandSearch {
    suspend fun send(
        address: Int,
        command: Int,
        transmit: (Int, Int) -> Unit,
        onSent: (Int, Int) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { transmit(address, command) }
            onSent(address, command)
        }
        currentCoroutineContext().ensureActive()
    }

    suspend fun scan(
        addresses: IntRange,
        commands: IntRange,
        pauseMillis: Long,
        transmit: (Int, Int) -> Unit,
        onSending: (Int, Int) -> Unit,
        onSent: (Int, Int) -> Unit,
        pause: suspend (Long) -> Unit = { delay(it) },
    ) {
        require(!addresses.isEmpty() && !commands.isEmpty())
        for (address in addresses) {
            for (command in commands) {
                currentCoroutineContext().ensureActive()
                onSending(address, command)
                send(address, command, transmit, onSent)
                if (address != addresses.last || command != commands.last) pause(pauseMillis)
            }
        }
    }
}
