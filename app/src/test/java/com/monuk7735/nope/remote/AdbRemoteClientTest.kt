package com.monuk7735.nope.remote

import com.monuk7735.nope.remote.adb.AdbRemoteClient
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbRemoteClientTest {
    @Test fun remoteKeysMapToAndroidKeyEvents() {
        assertEquals("input keyevent 3", AdbRemoteClient.commandForAction("home"))
        assertEquals("input keyevent 23", AdbRemoteClient.commandForAction("ok"))
        assertEquals("input keyevent 26", AdbRemoteClient.commandForAction("power"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownActionsCannotBecomeShellCommands() {
        AdbRemoteClient.commandForAction("home; reboot")
    }

    @Test fun successfulAdbRepliesAreAccepted() {
        AdbRemoteClient.validateResponse("ping", "nope_adb_ready\r\n__NOPE_EXIT_0\r\n")
        AdbRemoteClient.validateResponse("home", "__NOPE_EXIT_0\r\n")
    }

    @Test(expected = IOException::class)
    fun unsuccessfulExitCodeIsNotMistakenForSuccess() {
        AdbRemoteClient.validateResponse("home", "__NOPE_EXIT_01\r\n")
    }

    @Test(expected = IOException::class)
    fun pingRequiresTheExpectedResponse() {
        AdbRemoteClient.validateResponse("ping", "wrong\r\n__NOPE_EXIT_0\r\n")
    }
}
