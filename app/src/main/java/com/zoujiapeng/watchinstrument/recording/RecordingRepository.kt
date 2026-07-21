package com.zoujiapeng.watchinstrument.recording

import android.content.Context
import java.io.File

class RecordingRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "recordings").apply { mkdirs() }

    fun save(recording: PerformanceRecording) {
        val target = fileFor(recording.id)
        val temporary = File(directory, ".${recording.id}.tmp")
        temporary.writeText(RecordingCodec.encode(recording), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
        trimOldRecordings()
    }

    fun list(): List<PerformanceRecording> = directory
        .listFiles { file -> file.isFile && file.extension == "json" }
        .orEmpty()
        .mapNotNull { file -> runCatching { RecordingCodec.decode(file.readText(Charsets.UTF_8)) }.getOrNull() }
        .sortedByDescending(PerformanceRecording::createdAtEpochMs)

    fun delete(id: String): Boolean = fileFor(id).delete()

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]+"))) { "Invalid recording id" }
        return File(directory, "$id.json")
    }

    private fun trimOldRecordings() {
        list().drop(MAX_RECORDINGS).forEach { delete(it.id) }
    }

    companion object {
        const val MAX_RECORDINGS = 24
    }
}
