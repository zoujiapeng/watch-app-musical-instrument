package com.zoujiapeng.watchinstrument.recording

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion
import com.zoujiapeng.watchinstrument.model.Waveform
import org.json.JSONArray
import org.json.JSONObject

object RecordingCodec {
    private const val VERSION = 1

    fun encode(recording: PerformanceRecording): String {
        val events = JSONArray()
        recording.events.forEach { event ->
            events.put(
                JSONObject()
                    .put("t", event.atMs)
                    .put("a", event.action.name)
                    .put("k", event.voiceKey)
                    .put("n", event.midiNote ?: JSONObject.NULL)
                    .put("p", event.percussion?.name ?: JSONObject.NULL)
                    .put("w", event.waveform.name)
                    .put("v", event.velocity.toDouble()),
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("id", recording.id)
            .put("instrument", recording.instrument.id)
            .put("createdAt", recording.createdAtEpochMs)
            .put("duration", recording.durationMs)
            .put("events", events)
            .toString()
    }

    fun decode(json: String): PerformanceRecording {
        val root = JSONObject(json)
        require(root.optInt("version", 0) == VERSION) { "Unsupported recording version" }
        val sourceEvents = root.getJSONArray("events")
        val events = ArrayList<PerformanceEvent>(sourceEvents.length())
        for (index in 0 until sourceEvents.length()) {
            val item = sourceEvents.getJSONObject(index)
            events += PerformanceEvent(
                atMs = item.getLong("t").coerceAtLeast(0L),
                action = PerformanceAction.valueOf(item.getString("a")),
                voiceKey = item.getLong("k"),
                midiNote = item.takeUnless { it.isNull("n") }?.getInt("n"),
                percussion = item.takeUnless { it.isNull("p") }
                    ?.getString("p")
                    ?.let(Percussion::valueOf),
                waveform = item.optString("w", Waveform.SAW.name).let(Waveform::valueOf),
                velocity = item.optDouble("v", 1.0).toFloat().coerceIn(0.05f, 1f),
            )
        }
        return PerformanceRecording(
            id = root.getString("id"),
            instrument = InstrumentType.fromId(root.getString("instrument")),
            createdAtEpochMs = root.getLong("createdAt"),
            durationMs = root.getLong("duration").coerceAtLeast(0L),
            events = events.sortedBy(PerformanceEvent::atMs),
        )
    }
}
