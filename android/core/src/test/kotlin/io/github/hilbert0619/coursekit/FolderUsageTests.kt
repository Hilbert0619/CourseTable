package io.github.hilbert0619.coursekit

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderUsageTests {
    private fun makeFolder(): File {
        val folder = Files.createTempDirectory("FolderUsageTests").toFile()
        val nested = File(folder, "Wallpapers").apply { mkdirs() }
        File(folder, "a.tmp").writeBytes(ByteArray(1000))
        File(nested, "lake.jpg").writeBytes(ByteArray(250))
        File(nested, "moon.jpg").writeBytes(ByteArray(50))
        return folder
    }

    @Test
    fun sizesCountEveryFileBelow() {
        val folder = makeFolder()
        try {
            assertEquals(1300, FolderUsage.bytes(folder))
            assertEquals(3, FolderUsage.files(folder).size)
            assertEquals(300, FolderUsage.bytes(File(folder, "Wallpapers")))
            assertEquals(0, FolderUsage.bytes(File(folder, "missing")))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun emptyingKeepsTheFolder() {
        val folder = makeFolder()
        try {
            assertEquals(0, FolderUsage.removeContents(folder))
            assertTrue(folder.isDirectory)
            assertTrue(folder.listFiles()!!.isEmpty())
            assertEquals(0, FolderUsage.removeContents(File(folder, "missing")))
        } finally {
            folder.deleteRecursively()
        }
    }
}
