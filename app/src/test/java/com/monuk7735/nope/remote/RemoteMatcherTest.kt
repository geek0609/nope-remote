package com.monuk7735.nope.remote

import com.monuk7735.nope.remote.infrared.RemoteMatcher
import com.monuk7735.nope.remote.models.retrofit.DeviceCodesRetrofitModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMatcherTest {
    private fun profile(vararg codes: Pair<String, String>) =
        DeviceCodesRetrofitModel("TV", "Brand", codes.toMap())

    @Test fun groupsMatchingSignalsAndRetainsUnknownProfilesForEitherAnswer() {
        val profiles = listOf(
            profile("POWER" to "0xAA BB"),
            profile("PWR" to "aabb"),
            profile("POWER TOGGLE" to "CCDD"),
            profile("OTHER" to "EEFF"),
        )
        val candidates = profiles.indices.toSet()
        val test = checkNotNull(RemoteMatcher.next(profiles, candidates, emptySet()))
        assertEquals(setOf(0, 1), test.matching)
        assertEquals(setOf(2), test.different)
        assertEquals(setOf(0, 1, 3), RemoteMatcher.answer(candidates, test, true))
        assertEquals(setOf(2, 3), RemoteMatcher.answer(candidates, test, false))
    }

    @Test fun prioritizesABalancedSplitAndDoesNotRetestSkippedSignals() {
        val profiles = listOf(
            profile("POWER" to "AA", "MUTE" to "A1"),
            profile("POWER" to "AA", "MUTE" to "A2"),
            profile("POWER" to "AA", "MUTE" to "A1"),
            profile("POWER" to "BB", "MUTE" to "A2"),
        )
        val candidates = profiles.indices.toSet()
        val first = checkNotNull(RemoteMatcher.next(profiles, candidates, emptySet()))
        assertEquals("Mute", first.action)
        assertEquals(2, first.matching.size)
        val second = checkNotNull(RemoteMatcher.next(profiles, candidates, setOf(first.id)))
        assertNotEquals(first.id, second.id)
    }

    @Test fun ambiguousAliasesAreNotUsedToExcludeTheProfile() {
        val profiles = listOf(
            profile("POWER" to "AA", "PWR" to "BB"),
            profile("POWER" to "AA"),
        )
        val candidates = profiles.indices.toSet()
        val test = checkNotNull(RemoteMatcher.next(profiles, candidates, emptySet()))
        assertEquals(setOf(1), test.matching)
        assertEquals(setOf(0), RemoteMatcher.answer(candidates, test, false))
        assertEquals(candidates, RemoteMatcher.answer(candidates, test, true))
        assertNull(RemoteMatcher.next(profiles, candidates, setOf(test.id)))
    }

    @Test fun undoAndResetRestoreCandidatesAndSkippedSignals() {
        val initial = RemoteMatcher.Step(setOf(0, 1, 2), emptySet())
        val first = RemoteMatcher.Step(setOf(0, 1), setOf("power:AA"))
        val second = RemoteMatcher.Step(setOf(1), setOf("power:AA", "mute:BB"))
        val session = RemoteMatcher.Session(initial).advance(first).advance(second)
        assertEquals(first, session.undo().step)
        assertEquals(initial, session.undo().undo().step)
        assertEquals(initial, session.reset().step)
        assertEquals(emptyList<RemoteMatcher.Step>(), session.reset().history)
    }
}
