package com.example.pettranslator.ui.profile

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.DirectoryIteratorException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

data class CacheInspection(
    val cleanableBytes: Long = 0,
    val retainedBytes: Long = 0,
    val retainedFiles: Int = 0,
    val inspectionErrors: Int = 0,
)

/**
 * No current producer establishes an idle, safely deletable cache lifetime:
 * PCM belongs to recording; shared PNGs may still have external URI readers.
 * Unknown files are retained. Do not invent a deletion whitelist or clear entire roots.
 */
class SafeCacheInspector(private val roots: List<File>) {
    fun inspect(): CacheInspection {
        var bytes = 0L
        var files = 0
        var errors = 0
        var visited = 0
        fun inspectPath(path: Path, depth: Int) {
            if (++visited > 10_000 || depth > 32) { errors++; return }
            try {
                val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                when {
                    attrs.isSymbolicLink -> Unit // Never follow or count the target outside the inspected cache.
                    attrs.isRegularFile -> { bytes += attrs.size(); files++ }
                    attrs.isDirectory -> Files.newDirectoryStream(path).use { children ->
                        for (child in children) {
                            if (visited >= 10_000) { errors++; break }
                            inspectPath(child, depth + 1)
                        }
                    }
                    else -> files++
                }
            } catch (_: NoSuchFileException) {
                // Missing cache roots and concurrently removed cache files contain nothing to inspect.
            } catch (_: DirectoryIteratorException) { errors++ }
            catch (_: IOException) { errors++ }
            catch (_: SecurityException) { errors++ }
        }
        roots.map { it.toPath().toAbsolutePath().normalize() }.distinct().forEach { inspectPath(it, 0) }
        return CacheInspection(retainedBytes = bytes, retainedFiles = files, inspectionErrors = errors)
    }
}
