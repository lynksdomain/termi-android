package com.termi.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

private class OverlayEntry(
    val visibleState: MutableTransitionState<Boolean>,
    val order: Int,
    val content: @Composable () -> Unit,
)

// Monotonic counter stamped onto each OverlayEntry at registration time —
// `overlayEntries` is a hash-backed SnapshotStateMap with no insertion-order
// guarantee, so without this, a SECOND cover opened while a first is still
// showing (e.g. a picker opened from inside an already-open create-package
// screen) could render UNDERNEATH the first one instead of on top —
// visually invisible and untappable, even though `visible` was true and it
// was animating in correctly. Caught live: tapping "[ PICK SONGS ]" from
// inside the Create Package cover did nothing, because the picker cover was
// rendering behind it. Sorting by this in FullScreenOverlayHost fixes it.
private var nextOverlayOrder = 0

// Global registry of active full-screen "modals", rendered by the single
// FullScreenOverlayHost() call at the true root of the app (TermiNavHost).
//
// This used to wrap content in a platform Dialog (usePlatformDefaultWidth =
// false, decorFitsSystemWindows = false). That's the textbook Compose
// recipe for a true full-screen cover, and it worked on the emulator — but
// caught live on the real Anbernic RG Rotate hardware: the Dialog's window
// did NOT actually cover the full physical screen, leaving thin slivers of
// whatever was underneath visible top and bottom. Forcing the window
// directly (MATCH_PARENT + FLAG_LAYOUT_NO_LIMITS + setDecorFitsSystemWindows)
// didn't fix it either — this specific ROM's dialog window handling just
// doesn't cooperate. Registering content here instead, to be rendered as a
// plain sibling Box at the true top of the SAME window, sidesteps platform
// dialog window quirks entirely — and as a bonus, this also fully
// generalizes rule 3b (nested full-screen sub-flows reached from inside a
// tab-root screen): it no longer matters how deep in the tree
// FullScreenCover() is called from, since the actual content always
// renders at the true global top via this registry.
private val overlayEntries = mutableStateMapOf<Any, OverlayEntry>()

// Call exactly once, at the true root of the composition (TermiNavHost's
// outermost Box, after everything else so overlays paint on top).
@Composable
fun FullScreenOverlayHost() {
    Box(Modifier.fillMaxSize()) {
        overlayEntries.entries.sortedBy { it.value.order }.forEach { (entryKey, entry) ->
            key(entryKey) {
                AnimatedVisibility(
                    visibleState = entry.visibleState,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)),
                ) {
                    // A platform Dialog used to give us this for free — its
                    // own separate touch-input window fully blocked clicks
                    // to whatever was underneath. A plain composed Box does
                    // NOT: without an explicit click-consuming modifier,
                    // taps pass straight through to the screen behind it in
                    // the same window. Caught live: a rapid double-tap on
                    // "[BACK]" landed its second tap on the underlying
                    // screen's "[SETTINGS]" button (same top-right corner)
                    // the instant this modal's exit fade started, opening
                    // Settings unexpectedly and leaving the user stuck.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        entry.content()
                    }
                }
            }
        }
    }
}

// Takes `visible` instead of being conditionally composed by the caller
// (`if (show) { FullScreenCover(...) }`) — that pattern unmounts the entry
// the instant the caller's boolean flips false, killing the exit animation
// before it can play. Callers must render this unconditionally and just
// flip `visible`.
@Composable
fun FullScreenCover(visible: Boolean, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val entryKey = remember { Any() }
    val visibleState = remember { MutableTransitionState(false) }
    val latestContent by rememberUpdatedState(content)
    var everShown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) everShown = true
        visibleState.targetState = visible
    }

    DisposableEffect(entryKey) {
        onDispose { overlayEntries.remove(entryKey) }
    }

    val shouldExist = everShown && (visibleState.currentState || visibleState.targetState)
    LaunchedEffect(shouldExist) {
        if (shouldExist) {
            if (entryKey !in overlayEntries) {
                overlayEntries[entryKey] = OverlayEntry(visibleState, nextOverlayOrder++) { latestContent() }
            }
        } else {
            overlayEntries.remove(entryKey)
        }
    }

    if (visible) {
        BackHandler(enabled = true) { onDismissRequest() }
    }
}
