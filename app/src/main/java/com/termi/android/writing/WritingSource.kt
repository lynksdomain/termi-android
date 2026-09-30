package com.termi.android.writing

// Mirrors BookSource/MusicSource for Journal. Journal copies every import
// into WritingFolderManager's owned folder (same as Books), so this is a
// remembered grouping label, not a live external path — each
// `WritingDocument` tags itself with the source it came from via
// `WritingDocument.sourceRootPath`. No synthetic inbox source: WiFi
// Transfer writes into the exact same owned folder as regular imports (see
// WritingFolderManager.writingFolder / WiFiTransferServer), so there is no
// distinct inbox folder to model here.
data class WritingSource(
    val rootPath: String,
    val label: String,
    val isInbox: Boolean = false,
)
