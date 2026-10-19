package com.example.ui.media

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything needed to rebuild the editor after the app is closed. */
data class ProjectState(
    val sources: List<Source>,
    val segments: List<Segment>,
    val music: MusicTrack?,
    val videoVolume: Float
)

/**
 * Saves and loads the project as a JSON file in app storage.
 * Imported media is copied into app storage first, so it stays available after restart.
 */
object ProjectStore {

    private fun projectFile(context: Context) = File(context.filesDir, "project.json")

    private fun mediaDir(context: Context): File =
        File(context.filesDir, "media").apply { mkdirs() }

    /** Copies a picked file into app storage and returns a file:// Uri for the copy. Call from a background thread. */
    fun importCopy(context: Context, uri: Uri, tag: String): Uri {
        val dest = File(mediaDir(context), "${tag}_${System.nanoTime()}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read the selected file")
        input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
        return Uri.fromFile(dest)
    }

    fun save(context: Context, state: ProjectState) {
        val root = JSONObject()
        root.put("videoVolume", state.videoVolume.toDouble())

        val sources = JSONArray()
        for (s in state.sources) {
            sources.put(JSONObject().put("uri", s.uri.toString()).put("durationMs", s.durationMs))
        }
        root.put("sources", sources)

        val segments = JSONArray()
        for (s in state.segments) {
            segments.put(
                JSONObject()
                    .put("source", s.source)
                    .put("startMs", s.startMs)
                    .put("endMs", s.endMs)
            )
        }
        root.put("segments", segments)

        val m = state.music
        if (m != null) {
            root.put(
                "music",
                JSONObject()
                    .put("uri", m.uri.toString())
                    .put("fileDurationMs", m.fileDurationMs)
                    .put("volume", m.volume.toDouble())
                    .put("offsetMs", m.offsetMs)
                    .put("inMs", m.inMs)
                    .put("outMs", m.outMs)
            )
        }

        projectFile(context).writeText(root.toString())
    }

    /** Returns the saved project, or null if there is none or it cannot be read. */
    fun load(context: Context): ProjectState? {
        val file = projectFile(context)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())

            val sources = mutableListOf<Source>()
            val srcArr = root.optJSONArray("sources") ?: JSONArray()
            for (i in 0 until srcArr.length()) {
                val o = srcArr.getJSONObject(i)
                sources.add(Source(Uri.parse(o.getString("uri")), o.getLong("durationMs")))
            }

            val segments = mutableListOf<Segment>()
            val segArr = root.optJSONArray("segments") ?: JSONArray()
            for (i in 0 until segArr.length()) {
                val o = segArr.getJSONObject(i)
                segments.add(Segment(o.getInt("source"), o.getLong("startMs"), o.getLong("endMs")))
            }

            val music = if (root.has("music")) {
                val o = root.getJSONObject("music")
                MusicTrack(
                    uri = Uri.parse(o.getString("uri")),
                    fileDurationMs = o.getLong("fileDurationMs"),
                    volume = o.getDouble("volume").toFloat(),
                    offsetMs = o.getLong("offsetMs"),
                    inMs = o.getLong("inMs"),
                    outMs = o.getLong("outMs")
                )
            } else {
                null
            }

            ProjectState(
                sources = sources,
                segments = segments,
                music = music,
                videoVolume = root.optDouble("videoVolume", 1.0).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
}
