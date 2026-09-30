package com.termi.android.input

import android.view.KeyEvent
import android.view.MotionEvent

// Gamepad D-pad support: real hardware DPAD_UP/DOWN/LEFT/RIGHT key events
// are already consumed for free by Compose's built-in focus system (moving
// focus between any focusable() composable, which Modifier.clickable already
// applies internally) — no custom code needed for those. Two things Compose
// does NOT handle on its own, both wired from MainActivity:
//
// 1. Analog sticks (and hat-switch d-pads some controllers report instead of
//    real DPAD keys) arrive as continuous MotionEvent axis values, not key
//    events, so they never trigger a focus move by themselves. This class
//    thresholds those axis values (with hysteresis so focus doesn't flicker
//    right at the deadzone edge) and turns a held direction into synthetic
//    DPAD key down/up pairs, repeated at a fixed interval while held — the
//    same "move focus one step, then repeat" feel as pressing the physical
//    d-pad repeatedly.
// 2. BUTTON_A/BUTTON_B aren't the keycodes Compose's clickable listens for
//    (it listens for DirectionCenter/Enter) — see MainActivity.dispatchKeyEvent
//    for the A→DPAD_CENTER / B→BACK translation.
class AnalogStickToDpad {
    private var horizontalDirection = 0 // -1 left, 0 none, 1 right
    private var verticalDirection = 0
    private var lastHorizontalRepeatMs = 0L
    private var lastVerticalRepeatMs = 0L

    fun onMotionEvent(event: MotionEvent, dispatchKey: (keyCode: Int, action: Int) -> Unit) {
        val x = axisValue(event, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_X)
        val y = axisValue(event, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_Y)
        val now = event.eventTime

        horizontalDirection = updateAxis(
            value = x,
            currentDirection = horizontalDirection,
            negativeKey = KeyEvent.KEYCODE_DPAD_LEFT,
            positiveKey = KeyEvent.KEYCODE_DPAD_RIGHT,
            now = now,
            lastRepeatMs = lastHorizontalRepeatMs,
            setLastRepeatMs = { lastHorizontalRepeatMs = it },
            dispatchKey = dispatchKey,
        )
        verticalDirection = updateAxis(
            value = y,
            currentDirection = verticalDirection,
            negativeKey = KeyEvent.KEYCODE_DPAD_UP,
            positiveKey = KeyEvent.KEYCODE_DPAD_DOWN,
            now = now,
            lastRepeatMs = lastVerticalRepeatMs,
            setLastRepeatMs = { lastVerticalRepeatMs = it },
            dispatchKey = dispatchKey,
        )
    }

    private fun axisValue(event: MotionEvent, hatAxis: Int, stickAxis: Int): Float {
        val hat = event.getAxisValue(hatAxis)
        return if (hat != 0f) hat else event.getAxisValue(stickAxis)
    }

    private fun updateAxis(
        value: Float,
        currentDirection: Int,
        negativeKey: Int,
        positiveKey: Int,
        now: Long,
        lastRepeatMs: Long,
        setLastRepeatMs: (Long) -> Unit,
        dispatchKey: (Int, Int) -> Unit,
    ): Int {
        val newDirection = when {
            value <= -PRESS_THRESHOLD -> -1
            value >= PRESS_THRESHOLD -> 1
            currentDirection != 0 && kotlin.math.abs(value) > RELEASE_THRESHOLD -> currentDirection
            else -> 0
        }
        if (newDirection == 0) {
            setLastRepeatMs(0L)
            return 0
        }
        val keyCode = if (newDirection < 0) negativeKey else positiveKey
        val isNewPress = newDirection != currentDirection
        if (isNewPress || now - lastRepeatMs >= REPEAT_INTERVAL_MS) {
            dispatchKey(keyCode, KeyEvent.ACTION_DOWN)
            dispatchKey(keyCode, KeyEvent.ACTION_UP)
            setLastRepeatMs(now)
        }
        return newDirection
    }

    companion object {
        private const val PRESS_THRESHOLD = 0.5f
        private const val RELEASE_THRESHOLD = 0.3f
        private const val REPEAT_INTERVAL_MS = 220L
    }
}
