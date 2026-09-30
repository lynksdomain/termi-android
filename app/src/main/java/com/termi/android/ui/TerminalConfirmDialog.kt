package com.termi.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Kotlin/Compose port of TerminalActionSheet.swift's `.terminalConfirm` — a
// custom centered confirm card matching the terminal aesthetic, used
// everywhere the app needs a destructive/blocking confirmation instead of a
// stock Android AlertDialog. Animated to match the Swift source: backdrop
// fades (easeInOut, 150ms), card scales in from 0.92 + fades
// (spring(response: 0.28, dampingFraction: 0.82) here approximated as
// spring(dampingRatio = 0.82, stiffness = MediumLow)) — see
// android_port_rules memory on why Compose needs this spelled out explicitly.
@Composable
fun TerminalConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "[DELETE]",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
        targetValue = if (visibleState.targetState) 0.65f else 0f,
        animationSpec = tween(150),
        label = "confirmBackdrop",
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(onClick = ::requestDismiss),
        )
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)),
            exit = scaleOut(targetScale = 0.92f, animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)),
        ) {
            Column(
                modifier = Modifier
                    .width(270.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.background)
                    .background(theme.selectionBackground.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "╔═[ CONFIRM ]",
                    color = theme.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp, bottom = 8.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.25f)))
                Text(
                    text = title,
                    color = theme.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp, bottom = 4.dp),
                )
                Text(
                    text = message,
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 12.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.25f)))
                Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(onClick = ::requestDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "[CANCEL]",
                            color = theme.dim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Box(modifier = Modifier.width(0.5.dp).fillMaxSize().background(theme.accent.copy(alpha = 0.25f)))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                scope.launch {
                                    requestDismiss()
                                    delay(100)
                                    onConfirm()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = confirmLabel,
                            color = theme.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
