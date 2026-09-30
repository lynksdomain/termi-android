package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import java.util.UUID

// Compose ports of TerminalStatsComponents.swift. Sizes/paddings kept close
// to the iOS originals — the 720x720 Anbernic screen renders at roughly the
// same dp width as a small phone, so the same density works, but grids stay
// capped at 2 columns and headers stay single-line to survive the short height.

data class TerminalStat(
    val id: UUID = UUID.randomUUID(),
    val label: String,
    val value: String,
)

@Composable
fun TerminalStatsHeaderBar(
    title: String,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.selectionBackground.copy(alpha = 0.2f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "╔═[ $title ]",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (showDelete) {
            Text(
                text = "[DELETE]",
                color = theme.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(end = if (onBack != null) 12.dp else 0.dp)
                    .clickable(onClick = onDelete),
            )
        }
        if (onBack != null) {
            Text(
                text = "[BACK]",
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(end = if (onSettings != null) 12.dp else 0.dp)
                    .clickable(onClick = onBack),
            )
        }
        if (onSettings != null) {
            Text(
                text = "[SETTINGS]",
                color = theme.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onSettings),
            )
        }
    }
}

@Composable
fun TerminalYearSelector(years: List<Int>, selectedYear: Int?, onSelect: (Int?) -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.selectionBackground.copy(alpha = 0.15f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        YearTab("ALL TIME", selectedYear == null, theme.accent, theme.background, theme.dim) { onSelect(null) }
        Spacer(modifier = Modifier.width(6.dp))
        years.sortedDescending().forEachIndexed { index, year ->
            YearTab(year.toString(), selectedYear == year, theme.accent, theme.background, theme.dim) {
                onSelect(year)
            }
            if (index != years.lastIndex) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun YearTab(
    label: String,
    selected: Boolean,
    accent: Color,
    background: Color,
    dim: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = if (selected) background else dim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
fun TerminalSectionHeader(title: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "╠═[ $title ]",
        color = theme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        maxLines = 1,
    )
}

@Composable
fun TerminalStatCardGrid(stats: List<TerminalStat>, columns: Int = 2) {
    val theme = LocalTerminalTheme.current
    val rows = stats.chunked(columns)
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { stat ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = stat.value,
                            color = theme.accent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stat.label,
                            color = theme.dim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TerminalDataBackupSection(eventsCount: Int, onExport: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TerminalSectionHeader(title = "DATA BACKUP")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = "║  $eventsCount event(s) stored",
                color = theme.dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.secondary)
                    .clickable(onClick = onExport)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "[EXPORT CSV]",
                    color = theme.background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
