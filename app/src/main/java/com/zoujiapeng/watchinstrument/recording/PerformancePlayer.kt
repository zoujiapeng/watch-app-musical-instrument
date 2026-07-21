package com.zoujiapeng.watchinstrument.recording

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.zoujiapeng.watchinstrument.audio.RealtimeAudioEngine
import com.zoujiapeng.watchinstrument.audio.VoiceSpec
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

class PerformancePlayer(
    private val engine: RealtimeAudioEngine,
    private val onFinished: () -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val generation = AtomicLong(0L)

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var worker: Thread? = null

    fun play(recording: PerformanceRecording) {
        stop(notify = false)
        val thread = synchronized(stateLock) {
            val token = generation.incrementAndGet()
            isPlaying = true
            Thread(
                { playback(recording, token) },
                "watch-instrument-playback",
            ).also { created ->
                created.isDaemon = true
                worker = created
            }
        }
        thread.start()
    }

    fun stop(notify: Boolean = true) {
        val (thread, wasPlaying, stopToken) = synchronized(stateLock) {
            val active = isPlaying
            isPlaying = false
            val token = generation.incrementAndGet()
            val currentWorker = worker
            worker = null
            Triple(currentWorker, active, token)
        }
        thread?.interrupt()
        engine.allNotesOff()
        if (notify && wasPlaying) postFinished(stopToken)
    }

    private fun playback(recording: PerformanceRecording, token: Long) {
        val activeVoices = HashMap<Long, Long>()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            for (event in recording.events) {
                if (!isCurrent(token)) break
                val target = startedAt + event.atMs * 1_000_000L
                while (isCurrent(token)) {
                    val remaining = target - SystemClock.elapsedRealtimeNanos()
                    if (remaining <= 0L) break
                    LockSupport.parkNanos(remaining.coerceAtMost(5_000_000L))
                    if (Thread.interrupted()) return
                }
                if (!isCurrent(token)) break
                when (event.action) {
                    PerformanceAction.ON -> {
                        val newId = engine.noteOn(
                            VoiceSpec(
                                instrument = recording.instrument,
                                midiNote = event.midiNote ?: 60,
                                percussion = event.percussion,
                                waveform = event.waveform,
                                velocity = event.velocity,
                            ),
                        )
                        activeVoices[event.voiceKey] = newId
                    }
                    PerformanceAction.OFF -> activeVoices.remove(event.voiceKey)?.let(engine::noteOff)
                }
            }
        } finally {
            activeVoices.values.forEach(engine::noteOff)
            val completed = synchronized(stateLock) {
                if (generation.get() == token) {
                    isPlaying = false
                    worker = null
                    true
                } else {
                    false
                }
            }
            if (completed) postFinished(token)
        }
    }

    private fun isCurrent(token: Long): Boolean =
        isPlaying && generation.get() == token

    private fun postFinished(token: Long) {
        mainHandler.post {
            if (generation.get() == token && !isPlaying) onFinished()
        }
    }
}
