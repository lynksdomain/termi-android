package com.termi.android

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.termi.android.data.TermiDatabase
import com.termi.android.input.AnalogStickToDpad
import com.termi.android.input.GamepadFocusIndication
import com.termi.android.navigation.TermiNavHost
import com.termi.android.settings.AppSettings
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.theme.themeForId
import com.termi.android.ui.BootSequenceScreen
import com.termi.android.ui.CrtSettings
import com.termi.android.ui.LocalCrtSettings

// ComponentActivity inherits a restricted androidx.core dispatch method.
// Activity overrides are needed to translate physical controller keys.
@Suppress("RestrictedApi")
class MainActivity : ComponentActivity() {
    // Left analog stick / hat-switch d-pad -> synthetic DPAD key events (see
    // AnalogStickToDpad's header comment for why this can't just be Compose
    // focus-handling like the real hardware d-pad already is for free).
    private val playbackModel by viewModels<com.termi.android.music.PlaybackViewModel>()
    private val analogStickToDpad = AnalogStickToDpad()

    // A/B gamepad buttons aren't the keycodes Compose's clickable listens
    // for (DirectionCenter/Enter) or the keycode the system back stack
    // listens for, so they're translated here before falling through to the
    // normal dispatch path — every existing clickable()/BackHandler in the
    // app then just works with a controller, with no per-screen changes.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val translatedKeyCode = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> null
        }
        if (translatedKeyCode != null) {
            return super.dispatchKeyEvent(KeyEvent(event.action, translatedKeyCode))
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            analogStickToDpad.onMotionEvent(event) { keyCode, action ->
                super.dispatchKeyEvent(KeyEvent(action, keyCode))
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = TermiDatabase.getInstance(applicationContext)

        setContent {
            val playback by playbackModel.service.collectAsState()
            val appSettings = remember { AppSettings(applicationContext) }
            val themeId by appSettings.themeId.collectAsState()
            val showScanlines by appSettings.showScanlines.collectAsState()
            val showCrtVignette by appSettings.showCrtVignette.collectAsState()
            var bootComplete by remember { mutableStateOf(false) }
            val currentTheme = themeForId(themeId)
            CompositionLocalProvider(
                LocalTerminalTheme provides currentTheme,
                LocalCrtSettings provides CrtSettings(showScanlines, showCrtVignette),
                LocalIndication provides remember(currentTheme.accent, currentTheme.selectionBackground) {
                    GamepadFocusIndication(currentTheme.accent, currentTheme.selectionBackground)
                },
            ) {
                // TermiNavHost is ALWAYS composed, immediately — matching
                // iOS keeping ContentView (and its real onAppear loading)
                // mounted underneath the boot overlay the whole time. Boot
                // is rendered ON TOP as a plain overlay, not an if/else
                // swap: the swap used to mean none of TermiNavHost's real
                // loads (library scans, WritingManager.loadDocuments) even
                // started until boot finished, so there was still a real
                // chance of landing on a "loading" library right after
                // boot. Now those loads run in parallel with — and get a
                // head start from — the boot animation's own fixed
                // duration, same as iOS.
                Box(modifier = Modifier.fillMaxSize()) {
                    playback?.let {
                        TermiNavHost(db = db, appSettings = appSettings, playback = it)
                    }
                    if (!bootComplete) {
                        BootSequenceScreen(appSettings = appSettings, onComplete = { bootComplete = true })
                    }
                }
            }
        }
    }
}
