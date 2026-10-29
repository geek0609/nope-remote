package com.monuk7735.nope.remote.infrared

import com.monuk7735.nope.remote.models.retrofit.DeviceCodesRetrofitModel

/** Groups identical recorded signals, without assuming missing buttons are incompatible. */
object RemoteMatcher {
    data class Step(val candidates: Set<Int>, val tried: Set<String>)

    data class Session(
        val initial: Step,
        val step: Step = initial,
        val history: List<Step> = emptyList(),
    ) {
        fun advance(next: Step) = copy(step = next, history = history + step)
        fun undo() = if (history.isEmpty()) {
            this
        } else {
            copy(
                step = history.last(),
                history = history.dropLast(1),
            )
        }
        fun reset() = Session(initial)
    }

    data class Test(
        val action: String,
        val buttonName: String,
        val code: String,
        val matching: Set<Int>,
        val different: Set<Int>,
    ) {
        val id: String get() = "$action:$code"
    }

    private val aliases = mapOf(
        "POWER" to "Power", "PWR" to "Power", "POWER TOGGLE" to "Power",
        "VOL+" to "Volume up", "VOLUME+" to "Volume up", "VOL UP" to "Volume up",
        "VOLUME UP" to "Volume up", "VOL +" to "Volume up",
        "VOL-" to "Volume down", "VOLUME-" to "Volume down", "VOL DOWN" to "Volume down",
        "VOLUME DOWN" to "Volume down", "VOL -" to "Volume down",
        "MUTE" to "Mute", "CH+" to "Channel up", "CHANNEL+" to "Channel up",
        "CH UP" to "Channel up", "CHANNEL UP" to "Channel up",
        "MENU" to "Menu", "HOME" to "Home", "INPUT" to "Input", "SOURCE" to "Input",
        "OK" to "OK", "ENTER" to "OK", "SELECT" to "OK",
    )
    private val priority = listOf(
        "Power", "Volume up", "Mute", "Volume down", "Channel up", "Menu", "Home", "Input", "OK",
    )

    fun next(
        profiles: List<DeviceCodesRetrofitModel>,
        candidates: Set<Int>,
        tried: Set<String>,
    ): Test? {
        val byAction = mutableMapOf<String, MutableMap<Int, Pair<String, String>>>()
        for (index in candidates.sorted()) {
            val profile = profiles.getOrNull(index) ?: continue
            // A profile with multiple distinct signals for one action is ambiguous, so keep
            // it as unknown instead of accidentally eliminating it.
            val actions = profile.codes.entries.mapNotNull { (name, code) ->
                val action =
                    aliases[name.uppercase().replace('_', ' ').trim()] ?: return@mapNotNull null
                val normalized = code.replace(
                    "0x",
                    "",
                    ignoreCase = true,
                ).replace(Regex("\\s+"), "").uppercase()
                if (normalized.isEmpty()) null else Triple(action, name, normalized)
            }.groupBy { it.first }
            for ((action, entries) in actions) {
                if (entries.map { it.third }.distinct().size == 1) {
                    byAction.getOrPut(action) { mutableMapOf() }[index] =
                        entries.first().let { it.second to it.third }
                }
            }
        }
        val tests = mutableListOf<Test>()
        for ((action, entries) in byAction) {
            for ((code, group) in entries.entries.groupBy { it.value.second }) {
                val matching = group.map { it.key }.toSet()
                val different = entries.keys - matching
                val test = Test(action, group.first().value.first, code, matching, different)
                if (test.id !in tried) tests.add(test)
            }
        }
        // Minimize the larger possible remaining set, then prefer familiar test buttons.
        return tests.minWithOrNull(
            compareBy<Test> {
                maxOf(
                    candidates.size - it.different.size,
                    candidates.size - it.matching.size,
                )
            }
                .thenBy { priority.indexOf(it.action).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { it.code },
        )
    }

    fun answer(candidates: Set<Int>, test: Test, responded: Boolean): Set<Int> =
        candidates - if (responded) test.different else test.matching
}
