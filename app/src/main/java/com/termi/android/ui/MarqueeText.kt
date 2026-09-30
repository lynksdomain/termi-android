package com.termi.android.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

// Kotlin/Compose port of MarqueeText (used in MiniPlayerBar/PlayerView for
// title/artist/album) — Compose Foundation's built-in `basicMarquee()`
// already does exactly what the iOS version hand-rolled: scroll only when
// the text overflows its container, otherwise sit still. No need to
// reimplement the overflow-measuring/animation logic.
@Composable
fun MarqueeText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(),
    )
}
