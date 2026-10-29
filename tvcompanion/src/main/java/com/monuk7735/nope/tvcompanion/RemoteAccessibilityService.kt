package com.monuk7735.nope.tvcompanion

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.monuk7735.nope.tvbridge.BridgeProtocol
import com.monuk7735.nope.tvbridge.BridgeServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class RemoteAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var currentServer: BridgeServer? = null
        val listening: Boolean get() = currentServer?.boundPort != null
    }

    private var server: BridgeServer? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (server != null) return
        val key = BridgeProtocol.key(rotateSecret(this))
        server = BridgeServer(key) { payload -> dispatch(JSONObject(payload)).toString() }
            .also {
                currentServer = it
                it.start()
            }
    }

    private fun dispatch(request: JSONObject): JSONObject {
        val latch = CountDownLatch(1)
        var reply = JSONObject().put("ok", false).put("message", "TV did not respond")
        main.post {
            try {
                reply = execute(request)
            } catch (e: Exception) {
                reply = JSONObject().put("ok", false).put("message", e.message ?: "Action failed")
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return reply
    }

    private fun execute(request: JSONObject): JSONObject {
        val action = request.optString("action")
        val root = rootInActiveWindow
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val result = when (action) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "up", "down", "left", "right" -> {
                val direction = when (action) {
                    "up" -> View.FOCUS_UP
                    "down" -> View.FOCUS_DOWN
                    "left" -> View.FOCUS_LEFT
                    else -> View.FOCUS_RIGHT
                }
                (focused ?: root)?.focusSearch(
                    direction,
                )?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ==
                    true
            }
            "ok" -> focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            "volume_up", "volume_down", "mute" -> {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                val direction = when (action) {
                    "volume_up" -> AudioManager.ADJUST_RAISE
                    "volume_down" -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI,
                )
                true
            }
            "play_pause" -> {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                audio.dispatchMediaKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                )
                audio.dispatchMediaKeyEvent(
                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                )
                true
            }
            "paste" -> {
                val text = request.optString("text")
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Nope Remote", text))
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text,
                    )
                }
                focused?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true ||
                    focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
            }
            "copy" -> {
                if (focused?.isPassword == true) {
                    return JSONObject().put(
                        "ok",
                        false,
                    ).put("message", "Cannot copy a password field")
                }
                val selected = focused?.takeUnless { it.isPassword }?.let { node ->
                    val content = node.text
                    val start = node.textSelectionStart
                    val end = node.textSelectionEnd
                    if (content != null && start >= 0 && end > start && end <= content.length) {
                        content.subSequence(start, end).toString()
                    } else {
                        null
                    }
                }
                if (focused?.performAction(AccessibilityNodeInfo.ACTION_COPY) != true) {
                    return JSONObject().put(
                        "ok",
                        false,
                    ).put("message", "Select text on the TV first")
                }
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val value =
                    selected ?: clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                return if (value != null) {
                    JSONObject().put("ok", true).put("text", value)
                } else {
                    JSONObject().put(
                        "ok",
                        false,
                    ).put("message", "Android did not allow clipboard access")
                }
            }
            "ping" -> true
            else -> false
        }
        return JSONObject().put("ok", result).put(
            "message",
            if (result) "Done" else "This screen did not accept the action",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        server?.close()
        currentServer = null
        server = null
        super.onDestroy()
    }
}
