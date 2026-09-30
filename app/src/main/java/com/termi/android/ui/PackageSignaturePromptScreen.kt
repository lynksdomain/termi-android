package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.settings.AppSettings
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of PackageSignaturePromptView.swift — inline
// first-use signature capture, gating both PKGS entry points that need one
// (send and listen — the signature is what the other person sees during
// discovery, so it has to exist before either role can participate at
// all). Real iOS gap this closes: Android had a package-signature SETTING
// but never actually required it be set before sending/listening, so a
// package could go out (or a listen session start) with a blank sender
// name — this view + the two call-site gates in PackagesScreen.kt fix that.
enum class SignaturePromptContext { SENDING, LISTENING }

@Composable
fun PackageSignaturePromptScreen(context: SignaturePromptContext, onConfirm: (String) -> Unit) {
    val theme = LocalTerminalTheme.current
    var draft by remember { mutableStateOf("") }
    val canConfirm = draft.trim().isNotEmpty()
    val heading = if (context == SignaturePromptContext.SENDING) "BEFORE YOU SEND..." else "BEFORE YOU LISTEN..."

    fun confirm() {
        val trimmed = draft.trim()
        if (trimmed.isEmpty()) return
        onConfirm(trimmed)
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "╔═[ $heading ]",
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Text(
                text = "║  SIGN YOUR PACKAGES AS:",
                color = theme.foreground,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(text = "║ ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(text = "e.g. jdoe", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

            if (canConfirm) {
                Text(
                    text = "║  PREVIEW: ${AppSettings.formatPackageSignature(draft)}",
                    color = theme.accent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )
            }

            Text(
                text = "║  [ CONFIRM ]",
                color = if (canConfirm) theme.accent else theme.dim.copy(alpha = 0.4f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canConfirm) { confirm() }
                    .padding(vertical = 12.dp),
            )

            Text(
                text = "╚═══════════════",
                color = theme.dim.copy(alpha = 0.3f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        CRTOverlay()
    }
}
