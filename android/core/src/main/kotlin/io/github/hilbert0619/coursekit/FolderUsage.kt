package io.github.hilbert0619.coursekit

import java.io.File

/** The size of a folder the app keeps files in, and emptying it (Settings → Storage). */
object FolderUsage {
    /** A regular file and its size in bytes. */
    data class FileSize(val file: File, val bytes: Long)

    /** The regular files in [folder] and its subfolders, with their sizes in bytes; none if it doesn't exist. */
    fun files(folder: File): List<FileSize> {
        if (!folder.isDirectory) return emptyList()
        return folder.walkTopDown().filter { it.isFile }.map { FileSize(it, it.length()) }.toList()
    }

    /** Bytes taken by the files in [folder] and its subfolders. */
    fun bytes(folder: File): Long = files(folder).sumOf { it.bytes }

    /** Deletes everything inside [folder] but keeps the folder. Returns how many items couldn't be deleted. */
    fun removeContents(folder: File): Int {
        val items = folder.listFiles() ?: return 0
        return items.count { !it.deleteRecursively() }
    }
}
