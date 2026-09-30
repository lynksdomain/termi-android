package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme

// Shared underlined-field style used across Packages editor screens
// (matches PackageCreateScreen's own field styling for visual consistency).
@Composable
internal fun EditorField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, lines: Int = 1) {
    val theme = LocalTerminalTheme.current
    Text(text = "║  $label", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
    Box(modifier = Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = theme.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.foreground, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            maxLines = lines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
}
