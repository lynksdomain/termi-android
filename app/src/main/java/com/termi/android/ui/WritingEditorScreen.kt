package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlinx.coroutines.launch
import com.termi.android.stats.WritingStatsManager
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.theme.TerminalTheme
import com.termi.android.writing.WritingDocument
import com.termi.android.writing.WritingFolderManager
import com.termi.android.writing.WritingManager

// Kotlin/Compose port of WritingEditorView.swift. This pass adds the
// RAW/PROSE mode toggle, the collapsible [CTRL]/[HIDE] controls panel it
// lives in (word count moved INTO that panel, matching iOS's real info
// architecture — not always visible in the header), and the "[?]"
// markdown-help overlay. PROSE-mode rendering uses a VisualTransformation
// (headings/quote by line prefix, **bold**/*italic* by the same regexes
// iOS's NSRegularExpression uses) rather than iOS's UITextView
// attributedText mutation — arguably a cleaner fit for Compose, since it
// derives styling from the raw text without ever touching what's actually
// stored, but structurally the same idea: style the delimiters too, don't
// hide them.
// Also includes the Pomodoro timer (startPomodoro/tickPomodoro ported to a
// plain 1s coroutine loop in place of iOS's Timer.scheduledTimer),
// MiniPlayerBar in the controls panel (optional audioPlayer param — null
// when no player is available at the call site), and typewriter scroll —
// ported from typewriterScroll(_:) as a LaunchedEffect over the text
// field's cursor rect (via onTextLayout) and a manually-driven
// verticalScroll ScrollState, since Compose's BasicTextField has no
// built-in "keep the caret at this fraction of the viewport" behavior the
// way UITextView's contentOffset manipulation gives iOS for free.
private enum class WritingMode { RAW, PROSE }
private enum class PomodoroPhase { WORK, REST }
private const val WORK_SECONDS = 25 * 60
private const val REST_SECONDS = 5 * 60

@Composable
fun WritingEditorScreen(
    document: WritingDocument,
    writingManager: WritingManager,
    statsManager: WritingStatsManager,
    audioPlayer: com.termi.android.music.AudioPlayerEngine? = null,
    onClose: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    // TextFieldValue (not a plain String) so the cursor's current offset is
    // available for typewriter scroll below — mirrors iOS's need for
    // textView.selectedTextRange in typewriterScroll(_:).
    var fieldValue by remember(document.id) { mutableStateOf(TextFieldValue(document.content)) }
    val text = fieldValue.text
    val wordCount = remember(text) { WritingFolderManager.wordCount(text) }
    var mode by remember { mutableStateOf(WritingMode.RAW) }
    var showControls by remember { mutableStateOf(false) }
    var showMarkdownHelp by remember { mutableStateOf(false) }

    var pomodoroActive by remember { mutableStateOf(false) }
    var pomodoroPhase by remember { mutableStateOf(PomodoroPhase.WORK) }
    var pomodoroSecondsLeft by remember { mutableStateOf(WORK_SECONDS) }

    fun stopPomodoro() {
        pomodoroActive = false
        pomodoroSecondsLeft = WORK_SECONDS
        pomodoroPhase = PomodoroPhase.WORK
    }

    // Mirrors startPomodoro()/tickPomodoro() — a plain 1s coroutine loop
    // standing in for iOS's Timer.scheduledTimer, cancelled automatically
    // when pomodoroActive flips false or the editor leaves composition
    // (LaunchedEffect keyed on pomodoroActive, same lifecycle iOS gets
    // from invalidate() in onDisappear/stopPomodoro()).
    androidx.compose.runtime.LaunchedEffect(pomodoroActive) {
        if (!pomodoroActive) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            pomodoroSecondsLeft -= 1
            if (pomodoroSecondsLeft <= 0) {
                if (pomodoroPhase == PomodoroPhase.WORK) {
                    pomodoroPhase = PomodoroPhase.REST
                    pomodoroSecondsLeft = REST_SECONDS
                } else {
                    pomodoroPhase = PomodoroPhase.WORK
                    pomodoroSecondsLeft = WORK_SECONDS
                }
            }
        }
    }

    fun closeEditor() {
        writingManager.flushSave(text, document)
        statsManager.endSession(currentWordCount = wordCount)
        onClose()
    }

    BackHandler { closeEditor() }
    val latestText by rememberUpdatedState(text)
    val latestWordCount by rememberUpdatedState(wordCount)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(document.id, lifecycleOwner) {
        statsManager.beginSession(filename = document.title, currentWordCount = document.wordCount)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                writingManager.flushSave(latestText, document)
                statsManager.endSession(currentWordCount = latestWordCount)
            } else if (event == Lifecycle.Event.ON_START) {
                statsManager.beginSession(filename = document.title, currentWordCount = latestWordCount)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            writingManager.flushSave(latestText, document)
            statsManager.endSession(currentWordCount = latestWordCount)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "[CLOSE]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { closeEditor() }.padding(vertical = 8.dp),
                )
                Text(
                    text = document.title,
                    color = theme.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    text = "[?]",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { showMarkdownHelp = true }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Text(
                    text = if (showControls) "[HIDE]" else "[CTRL]",
                    color = if (showControls) theme.accent else theme.dim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (showControls) theme.accent.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { showControls = !showControls }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            if (showControls) {
                if (audioPlayer != null) {
                    val hasSong by audioPlayer.currentSong.collectAsState()
                    if (hasSong != null) {
                        MiniPlayerBar(player = audioPlayer, onOpen = {})
                        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.selectionBackground.copy(alpha = 0.1f))
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = "[${if (mode == WritingMode.RAW) "RAW" else "PROSE"}]",
                        color = if (mode == WritingMode.PROSE) theme.accent else theme.dim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (mode == WritingMode.PROSE) theme.accent.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { mode = if (mode == WritingMode.RAW) WritingMode.PROSE else WritingMode.RAW }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = "WORDS: $wordCount",
                        color = theme.dim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    PomodoroButton(
                        active = pomodoroActive,
                        phase = pomodoroPhase,
                        secondsLeft = pomodoroSecondsLeft,
                        onStart = { pomodoroPhase = PomodoroPhase.WORK; pomodoroSecondsLeft = WORK_SECONDS; pomodoroActive = true },
                        onStop = { stopPomodoro() },
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))
            }

            val scrollState = rememberScrollState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var viewportHeightPx by remember { mutableIntStateOf(0) }
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height },
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { new ->
                            fieldValue = new
                            writingManager.scheduledSave(new.text, document)
                        },
                        onTextLayout = { textLayout = it },
                        textStyle = TextStyle(
                            color = theme.foreground,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(theme.cursor),
                        visualTransformation = if (mode == WritingMode.PROSE) MarkdownVisualTransformation(theme) else VisualTransformation.None,
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    // Mirrors iOS's real textContainerInset bottom of 300pt —
                    // without slack scroll room past the actual last line,
                    // the caret can never be pinned at 40% down the
                    // viewport once it's near the end of a short document;
                    // there's simply nowhere left to scroll TO.
                    Box(modifier = Modifier.height(300.dp))
                }
            }

            // Mirrors iOS's typewriterScroll(_:): keeps the caret pinned near
            // 40% down the viewport as the user types, only scrolling down
            // once the caret would drift past the 50% midline (or snapping
            // back up if it jumped well above the target, e.g. after a
            // backspace across a line break) — not a scroll-to-caret-every-
            // keystroke that would otherwise jitter on every line wrap.
            LaunchedEffect(fieldValue.selection, textLayout, viewportHeightPx) {
                val layout = textLayout ?: return@LaunchedEffect
                if (viewportHeightPx <= 0) return@LaunchedEffect
                val cursorOffset = fieldValue.selection.end
                if (cursorOffset > layout.layoutInput.text.length) return@LaunchedEffect
                val cursorRect = layout.getCursorRect(cursorOffset)

                val targetY = viewportHeightPx * 0.40f
                val caretScreenY = cursorRect.top - scrollState.value
                val delta = caretScreenY - targetY
                val threshold = viewportHeightPx * 0.50f
                if (caretScreenY <= threshold && delta >= -20f) return@LaunchedEffect

                val maxOffset = scrollState.maxValue
                val newOffset = (scrollState.value + delta).toInt().coerceIn(0, max(0, maxOffset))
                scope.launch { scrollState.animateScrollTo(newOffset) }
            }
        }

        if (showMarkdownHelp) {
            MarkdownHelpOverlay(onDismiss = { showMarkdownHelp = false })
        }
    }
}

// Mirrors WritingEditorView.swift's applyMarkdown(to:) — same regexes, same
// "style the syntax characters too, don't hide them" behavior — but as a
// VisualTransformation over the raw text rather than mutating an
// NSAttributedString: Compose has no live-typing attributedText equivalent,
// and deriving display styling from the source of truth on every
// recomposition is a more natural fit here than trying to replicate
// UITextView's mutable-attributed-string model.
private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
private val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")

private class MarkdownVisualTransformation(private val theme: TerminalTheme) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            var loc = 0
            for (line in raw.split("\n")) {
                val range = loc until (loc + line.length)
                when {
                    line.startsWith("### ") -> addStyle(SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp), range.first, range.last + 1)
                    line.startsWith("## ") -> addStyle(SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp), range.first, range.last + 1)
                    line.startsWith("# ") -> addStyle(SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 22.sp), range.first, range.last + 1)
                    line.startsWith("> ") -> addStyle(SpanStyle(color = theme.dim), range.first, range.last + 1)
                }
                loc += line.length + 1
            }
            boldRegex.findAll(raw).forEach { m -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1) }
            italicRegex.findAll(raw).forEach { m -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
private fun MarkdownHelpOverlay(onDismiss: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(theme.background)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text(
                text = "╔═[ FORMATTING ]",
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = "> PROSE MODE ONLY",
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            SyntaxRow(syntax = "# ", label = "HEADING 1")
            SyntaxRow(syntax = "## ", label = "HEADING 2")
            SyntaxRow(syntax = "### ", label = "HEADING 3")
            SyntaxRow(syntax = "> ", label = "QUOTE")
            SyntaxRow(syntax = "**...**", label = "BOLD")
            SyntaxRow(syntax = "*...*", label = "ITALIC")
            Text(
                text = "> PLACE SYNTAX AT START OF LINE FOR HEADINGS/QUOTES,",
                color = theme.dim.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "> INLINE ANYWHERE FOR BOLD AND ITALIC.",
                color = theme.dim.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "[ GOT IT ]",
                color = theme.background,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

private val POMODORO_REST_COLOR = Color(0xFF39FF14)

@Composable
private fun PomodoroButton(
    active: Boolean,
    phase: PomodoroPhase,
    secondsLeft: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTerminalTheme.current
    if (active) {
        val phaseColor = if (phase == PomodoroPhase.WORK) theme.accent else POMODORO_REST_COLOR
        val mins = secondsLeft / 60
        val secs = secondsLeft % 60
        Row(
            modifier = modifier.clickable(onClick = onStop),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(5.dp).height(5.dp).clip(RoundedCornerShape(50)).background(phaseColor))
            Text(
                text = "  ${if (phase == PomodoroPhase.WORK) "FOCUS" else "BREAK"} %02d:%02d".format(mins, secs),
                color = phaseColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    } else {
        Text(
            text = "[TIMER 25:00]",
            color = theme.secondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = modifier.clickable(onClick = onStart),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SyntaxRow(syntax: String, label: String) {
    val theme = LocalTerminalTheme.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = syntax,
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = "─── $label",
            color = theme.foreground.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.1f)))
}
