package com.monuk7735.nope.remote

import com.monuk7735.nope.remote.infrared.IrCommandSearch
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrCommandSearchTest {
    @Test fun scansEveryCandidateAndPausesBetweenSignals() = runBlocking {
        val transmitted = mutableListOf<Pair<Int, Int>>()
        val selected = mutableListOf<Pair<Int, Int>>()
        val pauses = mutableListOf<Long>()
        IrCommandSearch.scan(
            2..3,
            4..5,
            1500,
            transmit = { address, command -> transmitted.add(address to command) },
            onSending = { _, _ -> },
            onSent = { address, command -> selected.add(address to command) },
            pause = { pauses.add(it) },
        )
        val expected = listOf(2 to 4, 2 to 5, 3 to 4, 3 to 5)
        assertEquals(expected, transmitted)
        assertEquals(expected, selected)
        assertEquals(listOf(1500L, 1500L, 1500L), pauses)
    }

    @Test fun failedTransmissionDoesNotBecomeSelected() = runBlocking {
        val selected = mutableListOf<Pair<Int, Int>>()
        try {
            IrCommandSearch.scan(
                1..1,
                0..2,
                1,
                transmit = { _, command -> if (command == 1) error("transmit failed") },
                onSending = { _, _ -> },
                onSent = { address, command -> selected.add(address to command) },
                pause = {},
            )
            error("Expected transmission failure")
        } catch (error: IllegalStateException) {
            assertEquals("transmit failed", error.message)
        }
        assertEquals(listOf(1 to 0), selected)
    }

    @Test fun stoppingDuringTransmissionRetainsTheCompletedSignal() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val selected = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
        val job = launch(Dispatchers.Default) {
            IrCommandSearch.scan(
                7..7,
                3..4,
                1,
                transmit = { _, _ ->
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                },
                onSending = { _, _ -> },
                onSent = { address, command -> selected.add(address to command) },
                pause = {},
            )
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertEquals(listOf(7 to 3), selected)
    }
}
