package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

// First-pass home/root screen — a terminal-style menu of ported screens.
// Stands in for ContentView.swift's app shell until more of the real app
// (library, reader, player) gets ported; for now it just routes to the
// stats screens we have.
data class HomeMenuItem(val index: Int, val label: String, val route: String)

@Composable
fun HomeScreen(items: List<HomeMenuItem>, onSelect: (String) -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalSectionHeader(title = "TERMI")
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item.route) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "[${item.index}]",
                            color = theme.dim,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Text(
                            text = item.label,
                            color = theme.foreground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
