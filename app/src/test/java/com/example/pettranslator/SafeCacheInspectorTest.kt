package com.example.pettranslator

import com.example.pettranslator.ui.profile.CacheInspection
import com.example.pettranslator.ui.profile.SafeCacheInspector
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeCacheInspectorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun emptyRootReportsNoCleanableOrRetainedFiles() {
        val root = temporaryFolder.newFolder("empty-cache")

        assertEquals(CacheInspection(), SafeCacheInspector(listOf(root)).inspect())

        assertTrue(root.isDirectory)
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun missingRootReportsZeroWithoutCreatingIt() {
        val root = File(temporaryFolder.root, "missing-cache")
        assertFalse(root.exists())

        assertEquals(CacheInspection(), SafeCacheInspector(listOf(root)).inspect())

        assertFalse(root.exists())
    }

    @Test fun recordingsSharesAndUnknownFilesAreCountedAndPreservedByteForByte() {
        val root = temporaryFolder.newFolder("populated-cache")
        val originals = linkedMapOf(
            "pet_recordings/capture_example.pcm" to byteArrayOf(0, 1, 127, -128, -1),
            "share/pet-result.png" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
            "unknown.bin" to byteArrayOf(9, 8, 7, 6)
        )
        originals.forEach { (path, bytes) ->
            File(root, path).apply { parentFile.mkdirs(); writeBytes(bytes) }
        }

        val result = SafeCacheInspector(listOf(root)).inspect()

        assertEquals(0L, result.cleanableBytes)
        assertEquals(originals.values.sumOf { it.size.toLong() }, result.retainedBytes)
        assertEquals(originals.size, result.retainedFiles)
        assertEquals(0, result.inspectionErrors)
        originals.forEach { (path, bytes) ->
            val file = File(root, path)
            assertTrue("Inspection must retain $path", file.isFile)
            assertArrayEquals("Inspection must preserve $path", bytes, file.readBytes())
        }
    }

    @Test fun duplicateRootsDoNotCountTheSameFileMoreThanOnce() {
        val root = temporaryFolder.newFolder("duplicate-cache")
        val bytes = byteArrayOf(42, 0, -1)
        val file = File(root, "unknown.bin").apply { writeBytes(bytes) }

        val result = SafeCacheInspector(listOf(root, root.absoluteFile, File(root.path))).inspect()

        assertEquals(CacheInspection(retainedBytes = bytes.size.toLong(), retainedFiles = 1), result)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun symbolicLinkDoesNotFollowOrModifyFilesOutsideTheRoot() {
        val root = temporaryFolder.newFolder("linked-cache")
        val outside = temporaryFolder.newFolder("outside-cache")
        val outsideBytes = byteArrayOf(10, 20, 30, 40, 50, 60)
        val outsideFile = File(outside, "private-data.bin").apply { writeBytes(outsideBytes) }
        val localBytes = byteArrayOf(1, 2, 3)
        val localFile = File(root, "local.bin").apply { writeBytes(localBytes) }
        val link = File(root, "outside-link").toPath()
        try {
            Files.createSymbolicLink(link, outside.toPath())
        } catch (error: IOException) {
            assumeNoException("This platform cannot create symbolic links", error)
        } catch (error: UnsupportedOperationException) {
            assumeNoException("This platform cannot create symbolic links", error)
        } catch (error: SecurityException) {
            assumeNoException("This platform cannot create symbolic links", error)
        }

        val result = SafeCacheInspector(listOf(root)).inspect()

        assertEquals(0L, result.cleanableBytes)
        assertEquals(localBytes.size.toLong(), result.retainedBytes)
        assertEquals(1, result.retainedFiles)
        assertTrue("Inspection must retain the symbolic link", Files.isSymbolicLink(link))
        assertArrayEquals(localBytes, localFile.readBytes())
        assertArrayEquals(outsideBytes, outsideFile.readBytes())
    }
}
