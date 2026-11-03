package com.example.pettranslator

import android.content.ContentUris
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.StaticLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.interpretation.PetInterpretationEngine
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
import com.example.pettranslator.domain.interpretation.PetInterpretationTemplate
import com.example.pettranslator.domain.interpretation.PetInterpretationTemplateCatalog
import com.example.pettranslator.domain.recognition.AudioQuality
import com.example.pettranslator.domain.recognition.PetRecognitionResult
import com.example.pettranslator.domain.recognition.RecognitionStatus
import com.example.pettranslator.domain.recognition.SoundEvent
import com.example.pettranslator.ui.result.ResultCardExporter
import com.example.pettranslator.ui.theme.PetFontFamilies
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises the production image export path, including Android font/emoji fallback. */
class ResultCardFontExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder get() = File(context.getExternalFilesDir(null), "a152-evidence").apply { mkdirs() }

    private fun result(template: PetInterpretationTemplate): PetInterpretationResult {
        val cat = template.petType == PetType.Cat
        val event = SoundEvent(if (cat) 78 else 70, if (cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(
            selectedPet = template.petType, scene = template.scene, detectedPet = template.petType,
            status = RecognitionStatus.SUCCESS, catScore = if (cat) .74f else .05f,
            dogScore = if (cat) .05f else .74f, otherScore = .08f,
            primarySound = event, soundEvents = listOf(event),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true),
            modelVersion = "font-export-fixture", ruleVersion = "font-export-fixture",
        )
        return checkNotNull(PetInterpretationEngine(
            now = { 1_800_000_000_000L }, templates = listOf(template),
        ).interpret(recognition, "a".repeat(64)))
    }

    private fun verifyLayout(result: PetInterpretationResult): StaticLayout {
        val layout = ResultCardExporter.createMonologueLayout(context, result)
        assertEquals("Export must keep the original monologue", result.innerMonologue, layout.text.toString())
        assertEquals(880, layout.width)
        assertEquals(43f, layout.paint.textSize, 0f)
        assertEquals("Export must use the same WenKai face as the UI configuration",
            PetFontFamilies.monologueTypeface(context), layout.paint.typeface)
        assertEquals("Packaged WenKai must load successfully on the test device",
            context.resources.getFont(R.font.pet_title_wenkai), layout.paint.typeface)
        assertTrue(layout.lineCount > 0)
        assertEquals("Every source character must be laid out", result.innerMonologue.length,
            layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertEquals("No ellipsis at line $line", 0, layout.getEllipsisCount(line))
            assertTrue("Line $line exceeds export text area",
                layout.getLineWidth(line) <= layout.width + 1f)
        }
        return layout
    }

    @Test fun missingFontResourceFallsBackToSystemFace() {
        val unavailable = object : ContextWrapper(context) {
            override fun getResources(): Resources = throw Resources.NotFoundException("Injected unavailable font resources")
        }
        assertEquals(Typeface.create("sans-serif", Typeface.NORMAL), PetFontFamilies.monologueTypeface(unavailable))
    }

    @Test fun savedGalleryImageRoundTripsAndOnlyOwnImageIsRemoved() {
        assumeTrue("Scoped MediaStore save validation requires API 29+", Build.VERSION.SDK_INT >= 29)
        val current = result(PetInterpretationTemplateCatalog.entries.first())
        val bitmap = ResultCardExporter.render(context, current)
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        var ownedUri: Uri? = null
        val startMs = System.currentTimeMillis()
        try {
            assertTrue("Production gallery save must succeed", ResultCardExporter.save(context, bitmap))
            val endMs = System.currentTimeMillis()
            val candidates = mutableListOf<Uri>()
            resolver.query(collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf("PetTranslator_%.png", "Pictures/PetTranslator/", (startMs / 1000L).toString()),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val filenameTime = name.removePrefix("PetTranslator_").removeSuffix(".png").toLongOrNull()
                    if (filenameTime != null && filenameTime in startMs..endMs) {
                        candidates += ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    }
                }
            }
            // A narrow filename timestamp range identifies this one save. Never delete
            // a broad collection of earlier PetTranslator images if the match is ambiguous.
            assertEquals("Locate exactly the image saved by this test", 1, candidates.size)
            val createdUri = candidates.single()
            ownedUri = createdUri
            val decoded = checkNotNull(resolver.openInputStream(createdUri)?.use { BitmapFactory.decodeStream(it) })
            try {
                assertEquals(bitmap.width, decoded.width)
                assertEquals(bitmap.height, decoded.height)
                assertEquals(bitmap.getPixel(0, 0), decoded.getPixel(0, 0))
                assertEquals(bitmap.getPixel(100, bitmap.height - 150), decoded.getPixel(100, decoded.height - 150))
                File(folder, "gallery-save-roundtrip.txt").writeText(
                    "Production MediaStore save and decode passed: ${decoded.width}x${decoded.height}; " +
                        "background/card sample colors preserved. Only this test's exact saved URI is removed in cleanup.\n",
                )
            } finally {
                decoded.recycle()
            }
        } finally {
            ownedUri?.let { resolver.delete(it, null, null) }
            bitmap.recycle()
        }
    }

    @Test fun allApprovedMonologuesExportCompleteInWenKai() {
        val templates = PetInterpretationTemplateCatalog.entries
        assertEquals(96, templates.size)
        val samples = PetType.entries.flatMap { pet ->
            val group = templates.filter { it.petType == pet }
            listOf(group.minBy { it.innerMonologue.length }.templateId,
                group.maxBy { it.innerMonologue.length }.templateId)
        }.toSet()
        val report = StringBuilder()
        for (template in templates) {
            val current = result(template)
            assertEquals(template.innerMonologue, current.innerMonologue)
            val layout = verifyLayout(current)
            val missing = current.innerMonologue.codePoints().toArray().distinct().filterNot {
                Character.isWhitespace(it) || layout.paint.hasGlyph(String(Character.toChars(it)))
            }
            assertTrue("${template.templateId}: missing glyphs $missing", missing.isEmpty())
            val bitmap = ResultCardExporter.render(context, current)
            try {
                assertEquals(1080, bitmap.width)
                assertTrue("Monologue must stay above the footer", 790 + layout.height < bitmap.height - 150)
                val shared = ResultCardExporter.writeShareFile(context, bitmap)
                assertTrue(shared.isFile && shared.length() > 1000)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(shared.absolutePath, bounds)
                assertEquals(bitmap.width, bounds.outWidth)
                assertEquals(bitmap.height, bounds.outHeight)
                if (template.templateId in samples) {
                    shared.copyTo(File(folder, "export-${template.templateId}.png"), overwrite = true)
                }
                report.appendLine("${template.templateId}: source=${current.innerMonologue.length} chars; " +
                    "lines=${layout.lineCount}; textHeight=${layout.height}px; bitmap=${bitmap.width}x${bitmap.height}; " +
                    "PNG=${shared.length()} bytes; no ellipsis/overflow")
            } finally {
                bitmap.recycle()
            }
        }
        File(folder, "export-96-layouts.txt").writeText(report.toString())
    }

    @Test fun longTextAndColoredEmojiRemainInsideExpandedExport() {
        val seed = PetInterpretationTemplateCatalog.entries.maxBy { it.innerMonologue.length }
        // Test-only stress copy; the production 96-line catalog remains unchanged.
        val text = "“喵～今天也想和你一起看看窗外，听听毛孩子的小心声！0123456789，。！？👀✨🐱🐶”".repeat(8)
        assertTrue(text.length > 35)
        val current = result(seed).copy(innerMonologue = text)
        val layout = verifyLayout(current)
        val missing = text.codePoints().toArray().distinct().filterNot {
            Character.isWhitespace(it) || layout.paint.hasGlyph(String(Character.toChars(it)))
        }
        assertTrue("Export font fallback has missing characters: $missing", missing.isEmpty())
        val bitmap = ResultCardExporter.render(context, current)
        try {
            assertTrue("Stress fixture must exercise vertical card expansion", bitmap.height > 1400)
            assertTrue(790 + layout.height < bitmap.height - 150)
            assertEquals("Expanded white card must extend below the original 1352px bottom",
                Color.WHITE, bitmap.getPixel(100, bitmap.height - 150))
            val shared = ResultCardExporter.writeShareFile(context, bitmap)
            shared.copyTo(File(folder, "export-long-stress.png"), overwrite = true)
        } finally {
            bitmap.recycle()
        }

        val emoji = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = PetFontFamilies.monologueTypeface(context); textSize = 80f; color = Color.BLACK
            }
            Canvas(emoji).apply { drawColor(Color.WHITE); drawText("🐱", 8f, 94f, paint) }
            var colored = 0
            for (y in 0 until emoji.height) for (x in 0 until emoji.width) {
                val pixel = emoji.getPixel(x, y)
                if (maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) -
                    minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 25) colored++
            }
            assertTrue("Emoji fallback must retain color", colored > 30)
            File(folder, "export-emoji.png").outputStream().use {
                emoji.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(folder, "export-stress-layout.txt").writeText(
                "Test-only long copy: ${text.length} UTF-16 units; lines=${layout.lineCount}; " +
                    "textHeight=${layout.height}px; no missing glyphs or ellipsis; colored emoji pixels=$colored.\n" +
                    "The production render/writeShareFile paths were exercised; no gallery entry or share intent was launched.\n",
            )
        } finally {
            emoji.recycle()
        }
    }
}
