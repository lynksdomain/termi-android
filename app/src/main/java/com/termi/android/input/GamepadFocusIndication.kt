package com.termi.android.input

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Installed once, app-wide, as LocalIndication in MainActivity — every
// existing Modifier.clickable(...) call site (the terminal UI's whole
// "[ LABEL ]" bracket-button convention) picks this up automatically with
// no per-screen changes, since Foundation's clickable defaults its
// `indication` param to LocalIndication.current. Without this, the default
// Material ripple is what would show on focus, which is both the wrong
// color for this theme and, on plain Text/Row containers with no ripple
// clip shape, often invisible — leaving gamepad users with no visible sign
// of which row is currently focused. D-pad/stick navigation itself needs no
// custom code (Compose's focus system already moves focus between any
// focusable() composable on DPAD_UP/DOWN/LEFT/RIGHT, and clickable already
// applies focusable() internally) — this is purely the missing visual.
//
// First pass drew only a thin accent-colored outline, which turned out to
// be too subtle to notice at a glance against this theme's own accent-
// colored text/selected-state styling — reported as "no highlight, I have
// no idea what I'm on". Now draws a solid selection-bar fill (the same
// visual language as the app's existing text-selection highlight) behind
// the row PLUS the border, so a focused row is unmistakable even at a
// glance on a handheld screen.
class GamepadFocusIndication(private val accent: Color, private val focusFill: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return GamepadFocusIndicationNode(interactionSource, accent, focusFill)
    }

    override fun equals(other: Any?): Boolean =
        other is GamepadFocusIndication && other.accent == accent && other.focusFill == focusFill
    override fun hashCode(): Int = accent.hashCode() * 31 + focusFill.hashCode()
}

private class GamepadFocusIndicationNode(
    private val interactionSource: InteractionSource,
    private val accent: Color,
    private val focusFill: Color,
) : Modifier.Node(), DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> { focused = true; invalidateDraw() }
                    is FocusInteraction.Unfocus -> { focused = false; invalidateDraw() }
                    is PressInteraction.Press -> { pressed = true; invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> { pressed = false; invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (focused) {
            drawRect(color = focusFill.copy(alpha = if (pressed) 0.55f else 0.35f))
        }
        drawContent()
        when {
            focused -> {
                val strokeWidth = 2.dp.toPx()
                val inset = strokeWidth / 2
                drawRect(
                    color = accent.copy(alpha = if (pressed) 1f else 0.85f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth),
                )
            }
            pressed -> drawRect(color = accent.copy(alpha = 0.15f))
        }
    }
}
