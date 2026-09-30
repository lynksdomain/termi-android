package com.termi.android.music

import android.content.Context
import java.io.File

// The library now REFERENCES files at their real, original location instead
// of copying them into an app-private folder — the whole point of picking a
// folder ("point at your Music folder") is that Android's broad file access
// (MANAGE_EXTERNAL_STORAGE) makes iOS's sandboxed copy-on-import unnecessary
// here. `musicFolder(context)` still exists as a private "inbox" — the only
// place we still write bytes ourselves is the WiFi Transfer server, which is
// receiving brand-new bytes over the network with no existing external file
// to reference; those still land here and get auto-registered into the
// library on next load (see MusicLibraryManager.loadLibrary).
object MusicFolderManager {

    private const val INBOX_FOLDER_NAME = "Termi Music"
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "mp4", "aac", "wav", "aiff", "flac", "ogg")

    fun musicFolder(context: Context): File {
        val folder = File(context.filesDir, INBOX_FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun scanInboxForAudioFiles(context: Context): List<File> {
        val folder = musicFolder(context)
        return folder.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.toList() ?: emptyList()
    }
}
