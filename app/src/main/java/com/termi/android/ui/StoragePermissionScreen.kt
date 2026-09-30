package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme

// Shown in place of FileBrowserScreen when MANAGE_EXTERNAL_STORAGE hasn't
// been granted yet — explains why, then hands off to the Settings screen
// (this permission can't be requested via a normal in-app dialog).
@Composable
fun StoragePermissionScreen(onRequestPermission: () -> Unit, onBack: (() -> Unit)? = null) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalStatsHeaderBar(
                title = "FILE ACCESS",
                showDelete = false,
                onDelete = {},
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Termi needs full file access to browse and import your music, journal entries, and books.",
                    color = theme.foreground,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                Text(
                    text = "[GRANT ACCESS]",
                    color = theme.background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(theme.accent)
                        .clickable(onClick = onRequestPermission)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        CRTOverlay()
    }
}
