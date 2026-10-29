package com.monuk7735.nope.remote

import com.monuk7735.nope.remote.repository.BuiltInRemoteRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRemoteRepositoryTest {
    @Test
    fun bundledFoxskyProfilesAreSelectableAndHaveValidSignals() = runBlocking {
        val asset = listOf(
            File("src/main/assets/foxsky_tv.psv"),
            File("app/src/main/assets/foxsky_tv.psv"),
        ).first { it.isFile }
        val repository = BuiltInRemoteRepository { asset.readText() }
        assertTrue(repository.isRepoInstalled())
        assertEquals(listOf("TV"), repository.getTypes().map { it.type })
        assertEquals(listOf("Foxsky"), repository.getBrands("tv").map { it.brand })
        assertTrue(repository.getBrands("fan").isEmpty())

        val profiles = repository.getCodes("TV", "foxsky", null)
        assertEquals(listOf("8914", "10261"), profiles.map { it.model })
        assertEquals(listOf(53, 57), profiles.map { it.codes.size })
        for (profile in profiles) {
            assertTrue(profile.codes.keys.containsAll(listOf("POWER", "VOL+", "VOL-", "OK")))
            for (hex in profile.codes.values) {
                val words = hex.split(" ").map { it.toInt(16) }
                assertEquals(0, words[0])
                assertEquals(36, words[2])
                assertEquals(0, words[3])
                assertEquals(72, words.size - 4)
                assertTrue(words.drop(4).all { it > 0 })
            }
        }
        val power = profiles.first().codes.getValue("POWER").split(" ").map { it.toInt(16) }
        assertEquals(listOf(341, 170), power.subList(4, 6))
        assertTrue(repository.getCodes("TV", "Other", null).isEmpty())
    }
}
