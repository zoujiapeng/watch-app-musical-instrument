package com.zoujiapeng.watchinstrument.audio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

class Metronome(
    private val engine: RealtimeAudioEngine,
    private val onBeat: (beat: Int, accented: Boolean) -> Unit = { _, _ -> },
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val generation = AtomicLong(0L)

    @Volatile
    var bpm: Int = 100
        set(value) {
            field = value.coerceIn(40, 240)
        }

    @Volatile
    var beatsPerBar: Int = 4
        set(value) {
            field = value.coerceIn(2, 6)
        }

    @Volatile
    var isRunning: Boolean = false
        private set

    private var worker: Thread? = null

    fun start() {
        val thread = synchronized(stateLock) {
            if (isRunning) return
            val token = generation.incrementAndGet()
            isRunning = true
            Thread(
                { runLoop(token) },
                "watch-instrument-metronome",
            ).also { created ->
                created.isDaemon = true
                worker = created
            }
        }
        thread.start()
    }

    fun stop() {
        val thread = synchronized(stateLock) {
            if (!isRunning && worker == null) return
            isRunning = false
            generation.incrementAndGet()
            worker.also { worker = null }
        }
        thread?.interrupt()
    }

    fun toggle() {
        if (isRunning) stop() else start()
    }

    private fun runLoop(token: Long) {
        var beat = 0
        var nextBeatNanos = SystemClock.elapsedRealtimeNanos()
        try {
            while (isCurrent(token)) {
                val accented = beat == 0
                engine.noteOn(
                    VoiceSpec(
                        instrument = InstrumentType.DRUMS,
                        percussion = if (accented) Percussion.METRONOME_HIGH else Percussion.METRONOME_LOW,
                        velocity = if (accented) 1f else 0.78f,
                    ),
                )
                val callbackBeat = beat
                mainHandler.post {
                    if (isCurrent(token)) onBeat(callbackBeat, accented)
                }
                beat = (beat + 1) % beatsPerBar

                val intervalNanos = 60_000_000_000L / bpm.coerceIn(40, 240)
                nextBeatNanos += intervalNanos
                while (isCurrent(token)) {
                    val remaining = nextBeatNanos - SystemClock.elapsedRealtimeNanos()
                    if (remaining <= 0L) break
                    LockSupport.parkNanos(remaining.coerceAtMost(5_000_000L))
                    if (Thread.interrupted()) return
                }
            }
        } finally {
            synchronized(stateLock) {
                if (generation.get() == token) {
                    isRunning = false
                    worker = null
                }
            }
        }
    }

    private fun isCurrent(token: Long): Boolean =
        isRunning && generation.get() == token
}
