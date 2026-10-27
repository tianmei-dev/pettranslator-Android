package com.example.pettranslator.ui.result

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.pettranslator.R
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
import com.example.pettranslator.domain.interpretation.displayTitle
import java.io.File
import java.io.FileOutputStream

internal object ResultCardExporter {
    fun render(context: Context, result: PetInterpretationResult): Bitmap {
        val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(37, 51, 105); textSize = 43f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layout = android.text.StaticLayout.Builder.obtain(result.innerMonologue, 0, result.innerMonologue.length,
            textPaint, 880).setLineSpacing(12f, 1f).build()
        val cardHeight = maxOf(1400, 790 + layout.height + 250)
        val bitmap = Bitmap.createBitmap(1080, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(255, 244, 249))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(RectF(46f, 48f, 1034f, 1352f), 54f, 54f, paint)
        paint.color = android.graphics.Color.rgb(248, 105, 174)
        paint.textSize = 40f; paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText("宠物翻译器 · 毛孩子的心声", 94f, 128f, paint)

        val petRes = if (result.petType.name == "Cat") R.drawable.pet_cat_main else R.drawable.pet_dog_main
        BitmapFactory.decodeResource(context.resources, petRes)?.let { pet ->
            val scaled = Bitmap.createScaledBitmap(pet, 410, (410f * pet.height / pet.width).toInt(), true)
            canvas.drawBitmap(scaled, (1080 - scaled.width) / 2f, 160f, paint)
            pet.recycle(); scaled.recycle()
        }
        paint.color = android.graphics.Color.rgb(23, 43, 117)
        paint.textSize = 34f; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText("${result.petType.title} · ${result.scene.title}", 94f, 660f, paint)
        paint.color = android.graphics.Color.rgb(239, 84, 157)
        paint.textSize = 30f
        canvas.drawText(result.coreThemeId.displayTitle, 94f, 730f, paint)
        canvas.save(); canvas.translate(94f, 790f); layout.draw(canvas); canvas.restore()
        paint.color = android.graphics.Color.rgb(126, 139, 183)
        paint.textSize = 24f
        canvas.drawText("趣味解读仅供娱乐参考", 94f, (cardHeight - 80).toFloat(), paint)
        return bitmap
    }

    fun save(context: Context, bitmap: Bitmap): Boolean {
        val name = "PetTranslator_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PetTranslator")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (error: Exception) { context.contentResolver.delete(uri, null, null); throw error }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PetTranslator")
            if (!dir.exists() && !dir.mkdirs()) return false
            val out = File(dir, name)
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("image/png"), null)
            true
        }
    }

    fun writeShareFile(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "pet-result.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "宠物趣味解读", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享毛孩子的心声"))
    }
}
