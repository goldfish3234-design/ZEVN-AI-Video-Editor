package com.example.ui.media

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Everything needed to rebuild one project in the editor.
 * [music] is the list of audio clips (the audio track).
 */
data class ProjectState(
    val name: String,
    val sources: List<Source>,
    val segments: List<Segment>,
    val music: List<MusicTrack>
)

/** One row in the Drafts list. */
data class ProjectSummary(val id: String, val name: String, val updatedAt: Long)

/**
 * Stores many projects. Each project lives in its own folder under filesDir/projects/<id>/
 * with project.json and its media copies, so creating a new project never overwrites an old one.
 */
object ProjectStore {

    private var currentId: String? = null
    var currentName: String = "Untitled"
        private set

    private fun rootDir(context: Context): File =
        File(context.filesDir, "projects").apply { mkdirs() }

    private fun projectDir(context: Context, id: String): File =
        File(rootDir(context), id).apply { mkdirs() }

    private fun pointerFile(context: Context) = File(context.filesDir, "current_project.txt")

    /** Moves the project saved by the older single-project version into the new folder layout. */
    private fun migrateLegacy(context: Context) {
        val legacy = File(context.filesDir, "project.json")
        if (!legacy.exists()) return
        try {
            val id = UUID.randomUUID().toString()
            val dest = File(projectDir(context, id), "project.json")
            legacy.copyTo(dest)
            legacy.delete()
        } catch (e: Exception) {
            // keep the legacy file if the move fails
        }
    }

    /** The id of the project being edited. Creates one if none exists yet. */
    private fun ensureCurrent(context: Context): String {
        currentId?.let { return it }
        val saved = pointerFile(context).takeIf { it.exists() }?.readText()?.trim()
        val id = if (!saved.isNullOrEmpty() && File(rootDir(context), saved).exists()) {
            saved
        } else {
            UUID.randomUUID().toString()
        }
        setCurrent(context, id)
        return id
    }

    private fun setCurrent(context: Context, id: String) {
        currentId = id
        pointerFile(context).writeText(id)
    }

    /** Starts a new, empty project. The previous project stays saved in Drafts. */
    fun newProject(context: Context, name: String) {
        val id = UUID.randomUUID().toString()
        projectDir(context, id)
        setCurrent(context, id)
        currentName = name
    }

    /** Opens a saved project and returns its state, or null if it cannot be read. */
    fun open(context: Context, id: String): ProjectState? {
        setCurrent(context, id)
        val state = read(context, id)
        if (state != null) currentName = state.name
        return state
    }

    /** Loads the current project (used when the editor opens and the timeline is empty). */
    fun loadCurrent(context: Context): ProjectState? {
        migrateLegacy(context)
        val id = ensureCurrent(context)
        val state = read(context, id)
        if (state != null) currentName = state.name
        return state
    }

    /** Copies a picked file into the current project's folder. Call from a background thread. */
    fun importCopy(context: Context, uri: Uri, tag: String): Uri {
        val dir = projectDir(context, ensureCurrent(context))
        val dest = File(dir, "${tag}_${System.nanoTime()}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read the selected file")
        input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
        return Uri.fromFile(dest)
    }

    /** Folder for files the app creates for the current project (e.g. extracted audio). */
    fun currentMediaDir(context: Context): File = projectDir(context, ensureCurrent(context))

    fun save(context: Context, state: ProjectState) {
        val id = ensureCurrent(context)
        currentName = state.name
        val root = JSONObject()
        root.put("name", state.name)
        root.put("updatedAt", System.currentTimeMillis())

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
                    .put("volume", s.volume.toDouble())
            )
        }
        root.put("segments", segments)

        val audio = JSONArray()
        for (m in state.music) {
            audio.put(
                JSONObject()
                    .put("uri", m.uri.toString())
                    .put("fileDurationMs", m.fileDurationMs)
                    .put("volume", m.volume.toDouble())
                    .put("offsetMs", m.offsetMs)
                    .put("inMs", m.inMs)
                    .put("outMs", m.outMs)
            )
        }
        root.put("audio", audio)

        File(projectDir(context, id), "project.json").writeText(root.toString())
    }

    /** All saved projects, newest first. */
    fun list(context: Context): List<ProjectSummary> {
        migrateLegacy(context)
        val out = mutableListOf<ProjectSummary>()
        for (dir in rootDir(context).listFiles().orEmpty()) {
            val f = File(dir, "project.json")
            if (!dir.isDirectory || !f.exists()) continue
            try {
                val o = JSONObject(f.readText())
                out.add(ProjectSummary(dir.name, o.optString("name", "Untitled"), o.optLong("updatedAt", 0L)))
            } catch (e: Exception) {
                // skip unreadable projects
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    /** Deletes a saved project and its media. */
    fun delete(context: Context, id: String) {
        projectDir(context, id).deleteRecursively()
        if (currentId == id) {
            currentId = null
            pointerFile(context).delete()
        }
    }

    private fun parseAudio(o: JSONObject): MusicTrack = MusicTrack(
        uri = Uri.parse(o.getString("uri")),
        fileDurationMs = o.getLong("fileDurationMs"),
        volume = o.optDouble("volume", 1.0).toFloat(),
        offsetMs = o.getLong("offsetMs"),
        inMs = o.getLong("inMs"),
        outMs = o.getLong("outMs")
    )

    private fun read(context: Context, id: String): ProjectState? {
        val file = File(projectDir(context, id), "project.json")
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
                segments.add(
                    Segment(
                        source = o.getInt("source"),
                        startMs = o.getLong("startMs"),
                        endMs = o.getLong("endMs"),
                        volume = o.optDouble("volume", 1.0).toFloat()
                    )
                )
            }

            val audio = mutableListOf<MusicTrack>()
            val audioArr = root.optJSONArray("audio")
            if (audioArr != null) {
                for (i in 0 until audioArr.length()) audio.add(parseAudio(audioArr.getJSONObject(i)))
            } else if (root.has("music")) {
                // older projects kept a single music clip
                audio.add(parseAudio(root.getJSONObject("music")))
            }

            ProjectState(
                name = root.optString("name", "Untitled"),
                sources = sources,
                segments = segments,
                music = audio
            )
        } catch (e: Exception) {
            null
        }
    }
}
