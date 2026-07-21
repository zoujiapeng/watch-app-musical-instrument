package com.zoujiapeng.watchinstrument.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class RealtimeAudioEngine(context: Context) {
    data class Diagnostics(
        val sampleRate: Int,
        val framesPerBurst: Int,
        val bufferFrames: Int,
        val encoding: Int,
    )

    private data class TrackSetup(
        val track: AudioTrack,
        val encoding: Int,
        val bufferFrames: Int,
    )

    private sealed interface Command {
        data class Add(val id: Long, val spec: VoiceSpec) : Command
        data class Release(val id: Long) : Command
        data object ReleaseAll : Command
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val commands = ConcurrentLinkedQueue<Command>()
    private val nextVoiceId = AtomicLong(1L)
    private val stateLock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var masterVolume = 0.8f

    @Volatile
    private var diagnosticsInternal: Diagnostics? = null

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    val diagnostics: Diagnostics?
        get() = diagnosticsInternal

    fun start(): Boolean = synchronized(stateLock) {
        if (running) return true
        releaseStaleTrackLocked()
        commands.clear()

        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it in 8_000..192_000 }
            ?: 48_000
        val framesPerBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it in 16..4_096 }
            ?: 256

        val setup = createTrack(sampleRate, framesPerBurst, AudioFormat.ENCODING_PCM_FLOAT)
            ?: createTrack(sampleRate, framesPerBurst, AudioFormat.ENCODING_PCM_16BIT)
            ?: return false

        val track = setup.track
        try {
            track.play()
        } catch (_: IllegalStateException) {
            track.release()
            return false
        }

        audioTrack = track
        diagnosticsInternal = Diagnostics(
            sampleRate = sampleRate,
            framesPerBurst = framesPerBurst,
            bufferFrames = setup.bufferFrames,
            encoding = setup.encoding,
        )
        running = true
        val thread = Thread(
            { renderLoop(track, sampleRate, framesPerBurst, setup.encoding) },
            "watch-instrument-audio",
        ).apply { isDaemon = true }
        audioThread = thread
        thread.start()
        true
    }

    fun stop() {
        val (track, thread) = synchronized(stateLock) {
            if (!running && audioTrack == null) return
            running = false
            commands.clear()
            val currentTrack = audioTrack
            val currentThread = audioThread
            audioTrack = null
            audioThread = null
            currentTrack to currentThread
        }

        stopAndFlush(track)
        try {
            thread?.join(750L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        track?.release()
    }

    fun setMasterVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
    }

    fun noteOn(spec: VoiceSpec): Long {
        val id = nextVoiceId.getAndIncrement()
        commands.offer(Command.Add(id, spec.copy(velocity = spec.velocity.coerceIn(0.05f, 1f))))
        return id
    }

    fun noteOff(voiceId: Long) {
        commands.offer(Command.Release(voiceId))
    }

    fun allNotesOff() {
        commands.offer(Command.ReleaseAll)
    }

    private fun createTrack(sampleRate: Int, framesPerBurst: Int, encoding: Int): TrackSetup? {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
            AudioFormat.ENCODING_PCM_16BIT -> Short.SIZE_BYTES
            else -> return null
        }
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            encoding,
        )
        if (minBufferBytes <= 0) return null

        val targetFrames = max(framesPerBurst * 2, (minBufferBytes + bytesPerSample - 1) / bytesPerSample)
        val targetBytes = targetFrames * bytesPerSample
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(targetBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }

        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            track?.release()
            return null
        }
        return TrackSetup(track, encoding, targetFrames)
    }

    private fun renderLoop(
        track: AudioTrack,
        sampleRate: Int,
        framesPerBurst: Int,
        encoding: Int,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mixBuffer = FloatArray(framesPerBurst)
        val pcm16Buffer = if (encoding == AudioFormat.ENCODING_PCM_16BIT) ShortArray(framesPerBurst) else null
        val voices = ArrayList<Voice>(MAX_POLYPHONY)

        try {
            while (running && audioTrack === track) {
                processCommands(voices, sampleRate)
                mixBuffer.fill(0f)

                val iterator = voices.iterator()
                while (iterator.hasNext()) {
                    if (!iterator.next().render(mixBuffer, mixBuffer.size)) {
                        iterator.remove()
                    }
                }

                val volume = masterVolume
                for (index in mixBuffer.indices) {
                    val scaled = mixBuffer[index] * volume
                    // Smooth saturation prevents the sum of several fingers from hard-clipping.
                    mixBuffer[index] = scaled / (1f + abs(scaled))
                }

                val result = try {
                    if (pcm16Buffer == null) {
                        track.write(mixBuffer, 0, mixBuffer.size, AudioTrack.WRITE_BLOCKING)
                    } else {
                        for (index in mixBuffer.indices) {
                            pcm16Buffer[index] = (mixBuffer[index].coerceIn(-1f, 1f) * Short.MAX_VALUE)
                                .roundToInt()
                                .toShort()
                        }
                        track.write(pcm16Buffer, 0, pcm16Buffer.size, AudioTrack.WRITE_BLOCKING)
                    }
                } catch (_: IllegalStateException) {
                    break
                }
                if (result < 0) break
            }
        } finally {
            voices.clear()
            val shouldRelease = synchronized(stateLock) {
                if (audioTrack === track) {
                    running = false
                    audioTrack = null
                    audioThread = null
                    true
                } else {
                    false
                }
            }
            if (shouldRelease) {
                stopAndFlush(track)
                track.release()
            }
        }
    }

    private fun processCommands(voices: MutableList<Voice>, sampleRate: Int) {
        while (true) {
            when (val command = commands.poll() ?: break) {
                is Command.Add -> {
                    if (voices.size >= MAX_POLYPHONY) voices.removeAt(0)
                    voices += VoiceFactory.create(command.id, command.spec, sampleRate)
                }
                is Command.Release -> voices.firstOrNull { it.id == command.id }?.noteOff()
                Command.ReleaseAll -> voices.forEach(Voice::noteOff)
            }
        }
    }

    private fun releaseStaleTrackLocked() {
        val stale = audioTrack ?: return
        running = false
        audioTrack = null
        audioThread?.interrupt()
        audioThread = null
        stopAndFlush(stale)
        stale.release()
    }

    private fun stopAndFlush(track: AudioTrack?) {
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: IllegalStateException) {
            // A concurrent shutdown or driver failure may already have stopped the track.
        }
    }

    companion object {
        const val MAX_POLYPHONY = 18
    }
}
