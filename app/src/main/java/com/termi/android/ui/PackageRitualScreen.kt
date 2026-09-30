package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.pkgs.PackageBridgeManager
import com.termi.android.pkgs.PackageRitualState
import com.termi.android.theme.LocalTerminalTheme
import java.io.File

// The real Termi Package ritual screen — listening/dialing, carrier
// detection, accept/decline, transfer, completion — replacing the old
// inline test-button UI now that there's a real container to send and a
// real storage manager to receive into. Kotlin/Compose equivalent of
// PackageRitualView.swift's state-by-state screens, though the exact
// per-state ASCII/animation treatment (the "baud-rate negotiation" bit
// discussed for foreign-carrier sends, pulsing dial rings, etc.) is still
// a later visual pass — this is the real state machine wired to the real
// transport, not yet the fully polished presentation.
private const val MY_PLATFORM = "android"

sealed class RitualMode {
    object Listen : RitualMode()
    data class Send(val file: File, val title: String, val mood: String) : RitualMode()
}

@Composable
fun PackageRitualScreen(
    bridgeManager: PackageBridgeManager,
    displayName: String,
    mode: RitualMode,
    onDone: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val state by bridgeManager.state.collectAsState()
    val peerPlatform by bridgeManager.peerPlatform.collectAsState()
    val isForeignCarrier = peerPlatform != null && peerPlatform != MY_PLATFORM

    LaunchedEffect(mode) {
        when (mode) {
            is RitualMode.Listen -> bridgeManager.startListening(displayName)
            is RitualMode.Send -> bridgeManager.startSending(displayName, mode.title, mode.mood)
        }
    }

    // Once the link is up on the sending side, hand the real file over —
    // this is the moment "DIALING/AWAITING ACCEPTANCE" becomes "TRANSMITTING".
    LaunchedEffect(state, mode) {
        if (mode is RitualMode.Send && state is PackageRitualState.LinkEstablished) {
            bridgeManager.sendPackage(mode.file)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            RitualStateText(state = state, isForeignCarrier = isForeignCarrier)

            when (val s = state) {
                is PackageRitualState.IncomingInvite -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row {
                        RitualButton(label = "[ Y - ACCEPT ]") { bridgeManager.acceptIncoming() }
                        Spacer(modifier = Modifier.width(12.dp))
                        RitualButton(label = "[ N - DECLINE ]", accent = false) {
                            bridgeManager.declineIncoming()
                            onDone()
                        }
                    }
                }
                is PackageRitualState.CompletedSend, is PackageRitualState.CompletedReceive,
                is PackageRitualState.Failed, is PackageRitualState.Cancelled,
                -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    RitualButton(label = "[ DONE ]") {
                        bridgeManager.reset()
                        onDone()
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "[ CANCEL ]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            bridgeManager.reset()
                            onDone()
                        },
                    )
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun RitualStateText(state: PackageRitualState, isForeignCarrier: Boolean) {
    val theme = LocalTerminalTheme.current
    val carrierTag = if (isForeignCarrier) " [FOREIGN CARRIER]" else ""
    val (label, color) = when (state) {
        is PackageRitualState.Idle -> "IDLE." to theme.dim
        is PackageRitualState.Listening -> "WAITING FOR CARRIER..." to theme.accent
        is PackageRitualState.Dialing -> "DIALING..." to theme.accent
        is PackageRitualState.CarrierDetected -> "CARRIER DETECTED: ${state.peerName}$carrierTag" to theme.accent
        is PackageRitualState.AwaitingAcceptance -> "AWAITING ACCEPTANCE... ${state.peerName} IS DECIDING" to theme.accent
        is PackageRitualState.IncomingInvite -> "INCOMING LINK...$carrierTag\nFROM: ${state.peerName}" to theme.accent
        is PackageRitualState.LinkEstablished -> "LINK ESTABLISHED.$carrierTag" to theme.accent
        is PackageRitualState.Transferring -> "TRANSMITTING... ${(state.progress * 100).toInt()}%" to theme.accent
        is PackageRitualState.CompletedSend -> "PACKAGE DELIVERED." to theme.accent
        is PackageRitualState.CompletedReceive -> "PACKAGE RECEIVED: \"${state.message}\"" to theme.accent
        is PackageRitualState.Failed -> "LINK LOST: ${state.reason}" to theme.secondary
        is PackageRitualState.Cancelled -> "LINK LOST." to theme.secondary
    }
    Text(
        text = "> $label",
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun RitualButton(label: String, accent: Boolean = true, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = if (accent) Color.Black else theme.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (accent) theme.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
