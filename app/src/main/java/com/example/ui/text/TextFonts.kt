package com.example.ui.text

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONObject
import java.io.File

/**
 * Built-in fonts plus fonts the user imported from the phone.
 * Imported fonts keep their real name (e.g. a Bengali font), can be renamed and deleted.
 * Files are stored in app storage under filesDir/fonts; names are kept in fonts.json.
 */
object FontLibrary {
    private val builtIn = listOf(
        "default" to "Default",
        "serif" to "Serif",
        "sans" to "Sans",
        "mono" to "Mono",
        "cursive" to "Handwriting"
    )

    fun fontDir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    private fun indexFile(context: Context): File = File(fontDir(context), "fonts.json")

    private fun readIndex(context: Context): MutableMap<String, String> {
        val f = indexFile(context)
        val map = mutableMapOf<String, String>()
        if (!f.exists()) return map
        try {
            val o = JSONObject(f.readText())
            o.keys().forEach { key -> map[key] = o.optString(key, key) }
        } catch (e: Exception) {
            // unreadable index: start again
        }
        return map
    }

    private fun writeIndex(context: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        indexFile(context).writeText(o.toString())
    }

    /** Imported fonts as (id, label). The id is "file:<stored file name>". */
    fun imported(context: Context): List<Pair<String, String>> {
        val index = readIndex(context)
        return fontDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.name != "fonts.json" }
            .sortedBy { it.name }
            .map { f -> "file:${f.name}" to (index[f.name] ?: f.nameWithoutExtension) }
    }

    /** (id, label) for every font the user can pick. */
    fun options(context: Context): List<Pair<String, String>> = builtIn + imported(context)

    fun label(context: Context, id: String): String =
        options(context).firstOrNull { it.first == id }?.second ?: "Default"

    fun family(context: Context, id: String): FontFamily = when {
        id.startsWith("file:") -> {
            val f = File(fontDir(context), id.removePrefix("file:"))
            if (f.exists()) {
                try {
                    FontFamily(Font(f))
                } catch (e: Exception) {
                    FontFamily.Default
                }
            } else {
                FontFamily.Default
            }
        }
        id == "serif" -> FontFamily.Serif
        id == "sans" -> FontFamily.SansSerif
        id == "mono" -> FontFamily.Monospace
        id == "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /**
     * Copies a picked font file into app storage and returns its id.
     * Keeps the original name (e.g. "NotoSansBengali") and rejects files that are not fonts.
     * Call from a background thread.
     */
    fun import(context: Context, uri: Uri): String {
        val original = displayName(context, uri) ?: "Font"
        val stored = "font_${System.currentTimeMillis()}.ttf"
        val dest = File(fontDir(context), stored)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read the font file")
        input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
        try {
            Typeface.createFromFile(dest)
        } catch (e: Exception) {
            dest.delete()
            throw IllegalStateException("This file is not a valid font")
        }
        val index = readIndex(context)
        index[stored] = original.substringBeforeLast('.').ifBlank { "Font" }
        writeIndex(context, index)
        return "file:$stored"
    }

    /** Changes the name shown for an imported font. */
    fun rename(context: Context, id: String, newName: String) {
        if (!id.startsWith("file:")) return
        val stored = id.removePrefix("file:")
        val index = readIndex(context)
        index[stored] = newName.trim().ifBlank { "Font" }
        writeIndex(context, index)
    }

    /** Deletes an imported font file. Text that used it falls back to the default font. */
    fun delete(context: Context, id: String) {
        if (!id.startsWith("file:")) return
        val stored = id.removePrefix("file:")
        File(fontDir(context), stored).delete()
        val index = readIndex(context)
        index.remove(stored)
        writeIndex(context, index)
    }
}
