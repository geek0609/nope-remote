package com.monuk7735.nope.remote

import com.monuk7735.nope.remote.infrared.patterns.IRPattern
import com.monuk7735.nope.remote.infrared.patterns.IRPatternType
import com.monuk7735.nope.remote.models.AirConditionerButtonGroup
import com.monuk7735.nope.remote.models.CustomRemoteLayout
import com.monuk7735.nope.remote.models.airConditionerButtonGroup
import com.monuk7735.nope.remote.models.database.RemoteButtonDBModel
import com.monuk7735.nope.remote.models.database.RemoteDataDBModel
import com.monuk7735.nope.remote.models.isAirConditionerType
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirConditionerButtonsTest {
    @Test
    fun recognizesAcTypesWithoutMatchingCamera() {
        assertTrue(isAirConditionerType("AC"))
        assertTrue(isAirConditionerType("Air Conditioner"))
        assertTrue(isAirConditionerType("Climate control"))
        assertFalse(isAirConditionerType("Camera"))
        assertFalse(isAirConditionerType("Vacuum"))
    }

    @Test
    fun groupsSavedControlsAndRetainsUnknownNames() {
        assertEquals(AirConditionerButtonGroup.POWER, airConditionerButtonGroup("Power Toggle"))
        assertEquals(AirConditionerButtonGroup.TEMPERATURE, airConditionerButtonGroup("TEMP_UP"))
        assertEquals(AirConditionerButtonGroup.TEMPERATURE, airConditionerButtonGroup("22C"))
        assertEquals(AirConditionerButtonGroup.MODE, airConditionerButtonGroup("MODE"))
        assertEquals(AirConditionerButtonGroup.FAN, airConditionerButtonGroup("FAN SPEED"))
        assertEquals(AirConditionerButtonGroup.SWING, airConditionerButtonGroup("SWING"))
        assertEquals(AirConditionerButtonGroup.OTHER, airConditionerButtonGroup("TURBO"))
    }

    @Test
    fun onlyNonemptyAcRemotesUseTheAcLayout() {
        val remote = RemoteDataDBModel(
            id = 1,
            name = "AC",
            brand = "Test",
            type = "Air Conditioner",
            added = Date(0),
            onScreenRemoteButtonDBS = emptyList(),
            offScreenRemoteButtonDBS = emptyList(),
        )
        val button = RemoteButtonDBModel(
            offsetX = 0f,
            offsetY = 0f,
            name = "TEMP+",
            irPattern = IRPattern(IRPatternType.Intervals, 38000, intArrayOf(100, 100)),
        )
        assertEquals(CustomRemoteLayout.NONE, remote.getCustomLayoutType())
        assertEquals(
            CustomRemoteLayout.AIR_CONDITIONER,
            remote.copy(offScreenRemoteButtonDBS = listOf(button)).getCustomLayoutType(),
        )
        assertEquals(
            CustomRemoteLayout.NONE,
            remote.copy(type = "Camera", offScreenRemoteButtonDBS = listOf(button))
                .getCustomLayoutType(),
        )
    }
}
