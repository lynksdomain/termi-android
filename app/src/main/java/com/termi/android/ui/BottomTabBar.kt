package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.navigation.AppTab
import com.termi.android.theme.LocalTerminalTheme

// Port of `terminalTabBar` from ContentView.swift — MUSIC / BOOKS / JOURNAL /
// PKGS, tabs separated by "│" dividers, no fill of its own (sits directly on
// the shared theme.background), just a top hairline in theme.accent.
@Composable
fun BottomTabBar(activeTab: AppTab, onSelect: (AppTab) -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.5f)))
        Row(modifier = Modifier.fillMaxWidth()) {
            AppTab.entries.forEachIndexed { index, tab ->
                val isActive = tab == activeTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "[${tab.label}]",
                        color = if (isActive) theme.accent else theme.dim,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (index != AppTab.entries.lastIndex) {
                    Text(
                        text = "│",
                        color = theme.dim,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
