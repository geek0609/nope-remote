package com.monuk7735.nope.remote.models

/** Group the codes actually present in a remote; never infer a new IR signal. */
internal enum class AirConditionerButtonGroup(val title: String) {
    POWER("Power"),
    TEMPERATURE("Temperature"),
    MODE("Mode"),
    FAN("Fan"),
    SWING("Swing"),
    OTHER("Other controls"),
}

internal fun isAirConditionerType(type: String): Boolean {
    val normalized = type.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    return normalized == "ac" ||
        normalized == "a c" ||
        normalized.contains("air conditioner") ||
        normalized.contains("air conditioning") ||
        normalized.contains("climate")
}

internal fun airConditionerButtonGroup(name: String): AirConditionerButtonGroup {
    val normalized = name.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()
    val words = normalized.split(' ').filter { it.isNotEmpty() }.toSet()
    return when {
        words.any { it in setOf("POWER", "PWR") } || normalized in setOf("ON", "OFF") ->
            AirConditionerButtonGroup.POWER
        words.any { it in setOf("TEMP", "TEMPERATURE", "DEGREES", "CELSIUS") } ||
            normalized.matches(Regex("[0-9]{2} ?C")) -> AirConditionerButtonGroup.TEMPERATURE
        words.any { it in setOf("MODE", "COOL", "HEAT", "DRY", "AUTO") } ->
            AirConditionerButtonGroup.MODE
        words.any { it in setOf("FAN", "SPEED") } -> AirConditionerButtonGroup.FAN
        words.any { it in setOf("SWING", "LOUVER", "LOUVRE", "VANE") } ->
            AirConditionerButtonGroup.SWING
        else -> AirConditionerButtonGroup.OTHER
    }
}
