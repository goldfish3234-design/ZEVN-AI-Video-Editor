package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream

/**
 * Remove BG for any subject (a person, an object, a product...). Uses ML Kit Subject Segmentation.
 * The model is downloaded by Google Play services, so no model file is needed in the project.
 * The result is a PNG with a transparent background, used as a sticker.
 * Call from a background thread.
 */
object BackgroundRemover {

    // Large photos are made smaller first, so the work stays quick
    private const val MAX_SIDE = 1280

    /** Reads [uri], removes its background and saves a PNG in the app folder. Returns the file, or null. */
    fun removeToFile(context: Context, uri: Uri): File? {
        val src = decodeScaled(context, uri) ?: return null
        val cut = removeFromBitmap(src) ?: return null
        val dir = File(context.filesDir, "bg_removed").apply { mkdirs() }
        val out = File(dir, "cutout_${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return null
        var sample = 1
        while (maxSide / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Returns a copy of [src] with the background made transparent, or null if the model is not
     * available or the segmentation fails (for example when there is no internet on the first use).
     */
    fun removeFromBitmap(src: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0)))
            val fg = result.foregroundBitmap ?: return null
            val sized = if (fg.width == src.width && fg.height == src.height) {
                fg
            } else {
                Bitmap.createScaledBitmap(fg, src.width, src.height, true)
            }
            return sized.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            return null
        } finally {
            segmenter.close()
        }
    }
}
