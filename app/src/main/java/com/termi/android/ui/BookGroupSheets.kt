package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.books.Book
import com.termi.android.books.BookGroup
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of BookLibraryView.swift's createGroupSheet — shared
// create+edit form (fields are pre-seeded by the caller when editing).
@Composable
fun BookGroupEditorScreen(
    group: BookGroup?,
    allBooks: List<Book>,
    onSave: (name: String, description: String, filenames: List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    var name by remember { mutableStateOf(group?.name ?: "") }
    var description by remember { mutableStateOf(group?.groupDescription ?: "") }
    var selected by remember { mutableStateOf(group?.bookFilenames?.toSet() ?: emptySet()) }
    var search by remember { mutableStateOf("") }

    val filtered = if (search.isBlank()) {
        allBooks
    } else {
        allBooks.filter { it.title.contains(search, ignoreCase = true) || it.author.contains(search, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (group == null) "┌─[ CREATE GROUP ]" else "┌─[ EDIT GROUP ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp, horizontal = 12.dp),
                )
                Text(
                    text = if (group == null) "[CREATE]" else "[SAVE]",
                    color = if (name.isNotBlank()) theme.accent else theme.dim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(enabled = name.isNotBlank()) { onSave(name.trim(), description.trim(), selected.toList()) }
                        .padding(vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(text = "│ NAME> ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        decorationBox = { inner ->
                            if (name.isEmpty()) Text(text = "Group name", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            inner()
                        },
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
                Text(text = "│ DESC > ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        decorationBox = { inner ->
                            if (description.isEmpty()) Text(text = "Optional description", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            inner()
                        },
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                text = "╠══════════════════════════════════════════════╣",
                color = theme.dim.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.selectionBackground.copy(alpha = 0.25f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "│ SEARCH> ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        decorationBox = { inner ->
                            if (search.isEmpty()) Text(text = "Find a book...", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            inner()
                        },
                    )
                }
                if (search.isNotEmpty()) {
                    Text(
                        text = "[X]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { search = "" }.padding(horizontal = 6.dp),
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.selectionBackground.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "│ ${selected.size} SELECTED",
                    color = if (selected.isEmpty()) theme.dim else theme.accent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (selected.isNotEmpty()) {
                    Text(
                        text = "[CLEAR ALL]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { selected = emptySet() }.padding(end = 8.dp),
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.3f)))

            if (filtered.isEmpty()) {
                Text(
                    text = "[ NO BOOKS IN LIBRARY ]",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered) { book ->
                        val isSelected = book.file.name in selected
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) theme.selectionBackground.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    selected = if (isSelected) selected - book.file.name else selected + book.file.name
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isSelected) "[X]" else "[ ]",
                                    color = if (isSelected) theme.accent else theme.dim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(32.dp),
                                )
                                Column {
                                    Text(
                                        text = book.title,
                                        color = if (isSelected) theme.accent else theme.foreground,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = book.author,
                                        color = theme.dim,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
        CRTOverlay()
    }
}

// Kotlin/Compose port of BookLibraryView.swift's addToGroupSheet.
@Composable
fun AddToGroupScreen(
    book: Book,
    groups: List<BookGroup>,
    onToggle: (BookGroup) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "┌─[ ADD TO GROUP ]",
                        color = theme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(text = book.title, color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            if (groups.isEmpty()) {
                Text(
                    text = "[ NO GROUPS — CREATE ONE FIRST ]",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(groups) { group ->
                        val isMember = book.file.name in group.bookFilenames
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(group) }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isMember) "[X]" else "[ ]",
                                    color = if (isMember) theme.accent else theme.dim,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Text(
                                    text = group.name,
                                    color = theme.foreground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "(${group.bookFilenames.size})",
                                    color = theme.dim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
        CRTOverlay()
    }
}
