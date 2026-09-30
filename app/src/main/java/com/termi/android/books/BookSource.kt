package com.termi.android.books

// Mirrors MusicSource (see com.termi.android.music.MusicSource) but for a
// COPY-into-app-folder library rather than a reference-in-place one: Books
// always copies the EPUB into BookFolderManager's owned folder, so a
// `BookSource` doesn't point at where the file physically lives today —
// it's purely a remembered grouping label (`rootPath` is still the
// identity/dedupe key) that each imported `Book` tags itself with via
// `Book.sourceRootPath`. Books has no genuinely separate WiFi inbox folder
// (WiFiTransferServer writes straight into the same owned folder regular
// imports use — see BookFolderManager.booksFolder), so unlike Music there
// is no synthetic inbox source here: `isInbox` exists only for shape parity
// with MusicSource and is always false in practice.
data class BookSource(
    val rootPath: String,
    val label: String,
    val isInbox: Boolean = false,
)
