package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

/**
 * Remove BG: separates the person from the background of a photo with ML Kit Selfie Segmentation
 * (the model is inside the library, no file to download). The result is a PNG with a transparent
 * background, which is used as a sticker. Call from a background thread.
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

    /** Returns a copy of [src] where the background pixels are transparent. */
    fun removeFromBitmap(src: Bitmap): Bitmap? {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0)))
            val mw = mask.width
            val mh = mask.height
            if (mw <= 0 || mh <= 0) return null
            // the mask holds one confidence value (0..1, 1 = person) per mask pixel, as floats
            val floats = mask.buffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()

            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            for (y in 0 until h) {
                val my = (y * mh / h).coerceIn(0, mh - 1)
                for (x in 0 until w) {
                    val mx = (x * mw / w).coerceIn(0, mw - 1)
                    val conf = floats.get(my * mw + mx).coerceIn(0f, 1f)
                    val i = y * w + x
                    val alpha = (conf * 255f).toInt()
                    pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
                }
            }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return out
        } catch (e: Exception) {
            return null
        } finally {
            segmenter.close()
        }
    }
}
