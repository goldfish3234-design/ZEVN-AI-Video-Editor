package com.example.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Copies a picture picked from the phone into the project folder, with a file extension that matches
 * its type, and checks that it really is a picture. Call from a background thread.
 */
object ImageImport {

    /** Returns a file:// address of the copied picture, or null if it cannot be read as a picture. */
    fun copy(context: Context, uri: Uri): String? {
        return try {
            val mime = context.contentResolver.getType(uri) ?: ""
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val dir = File(ProjectStore.currentMediaDir(context), "imports").apply { mkdirs() }
            val dest = File(dir, "img_${System.nanoTime()}.$ext")

            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }

            // make sure the file is a picture that can be decoded
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dest.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                dest.delete()
                null
            } else {
                Uri.fromFile(dest).toString()
            }
        } catch (e: Exception) {
            null
        }
    }
}
