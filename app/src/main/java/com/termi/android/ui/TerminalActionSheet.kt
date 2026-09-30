package com.termi.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Kotlin/Compose port of TerminalActionSheet.swift's `.terminalActionSheet` —
// a custom bottom-anchored action sheet matching the terminal aesthetic,
// used everywhere the app needs a context menu instead of a stock Android
// dialog/menu (per the project's "no stock Android UI where iOS is custom"
// rule — see android_port_rules memory).
//
// Animated to match the Swift source exactly: backdrop fades in/out
// (easeInOut, 180ms), panel slides up from the bottom + fades
// (spring(response: 0.3, dampingFraction: 0.8) ≈ spring(dampingRatio = 0.8,
// stiffness = MediumLow) here) — iOS gets this for free from
// `.transition()`/`.animation()`; Compose needs it built explicitly (see
// android_port_rules memory on presentation animations).
data class TerminalAction(
    val label: String,
    val icon: String,
    val destructive: Boolean = false,
    val action: () -> Unit,
)

@Composable
fun TerminalActionSheet(actions: List<TerminalAction>, onDismiss: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val scope = rememberCoroutineScope()
    val visibleState = remember { MutableTransitionState(false) }
    var hasAppeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visibleState.targetState = true }
    LaunchedEffect(visibleState.currentState) {
        if (visibleState.currentState) hasAppeared = true
        if (hasAppeared && !visibleState.currentState && !visibleState.targetState) onDismiss()
    }
    fun requestDismiss() {
        visibleState.targetState = false
    }

    val backdropAlpha by animateFloatAsState(
        targetValue = if (visibleState.targetState) 0.55f else 0f,
        animationSpec = tween(180),
        label = "actionSheetBackdrop",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(onClick = ::requestDismiss),
        )
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180),
            ) + fadeOut(animationSpec = tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background)
                    .background(theme.selectionBackground.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = "SELECT ACTION",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp, bottom = 6.dp),
                )
                actions.forEach { action ->
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.selectionBackground.copy(alpha = 0.3f))
                            .clickable {
                                scope.launch {
                                    requestDismiss()
                                    delay(150)
                                    action.action()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = action.icon,
                            color = if (action.destructive) theme.secondary else theme.accent,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            text = action.label,
                            color = if (action.destructive) theme.secondary else theme.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = ::requestDismiss)
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "[CANCEL]",
                        color = theme.dim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
