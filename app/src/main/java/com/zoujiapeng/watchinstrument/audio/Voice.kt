package com.zoujiapeng.watchinstrument.audio

import com.zoujiapeng.watchinstrument.model.NoteMath
import com.zoujiapeng.watchinstrument.model.Percussion
import com.zoujiapeng.watchinstrument.model.Waveform
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal interface Voice {
    val id: Long
    fun noteOff()

    /** Adds samples to [output]. Returns true while the voice is alive. */
    fun render(output: FloatArray, frameCount: Int): Boolean
}

internal class PianoVoice(
    override val id: Long,
    midiNote: Int,
    velocity: Float,
    private val sampleRate: Int,
) : Voice {
    private val phases = DoubleArray(7)
    private val increments = DoubleArray(7)
    private val amplitudes = floatArrayOf(1f, 0.58f, 0.34f, 0.22f, 0.14f, 0.09f, 0.05f)
    private val decayMultipliers = DoubleArray(7)
    private val random = Random(id xor midiNote.toLong())
    private var age = 0
    private var released = false
    private var releaseGain = 1.0
    private val level = velocity.coerceIn(0.05f, 1f) * 0.31f

    init {
        val frequency = NoteMath.midiToFrequency(midiNote)
        val detune = doubleArrayOf(1.0, 2.001, 3.004, 4.009, 5.012, 6.018, 7.025)
        val decayRates = doubleArrayOf(0.72, 1.18, 1.62, 2.10, 2.65, 3.20, 3.90)
        for (i in phases.indices) {
            increments[i] = 2.0 * PI * frequency * detune[i] / sampleRate
            decayMultipliers[i] = exp(-decayRates[i] / sampleRate)
        }
    }

    override fun noteOff() {
        released = true
    }

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        val attackSamples = max(1, (sampleRate * 0.004).roundToInt())
        val releaseMultiplier = exp(-1.0 / (sampleRate * 0.22))
        repeat(frameCount) { frame ->
            var sample = 0.0
            for (partial in phases.indices) {
                sample += sin(phases[partial]) * amplitudes[partial]
                phases[partial] += increments[partial]
                if (phases[partial] >= 2.0 * PI) phases[partial] -= 2.0 * PI
                amplitudes[partial] = (amplitudes[partial] * decayMultipliers[partial]).toFloat()
            }
            val attack = (age.toFloat() / attackSamples).coerceAtMost(1f)
            val hammer = if (age < sampleRate / 70) {
                ((random.nextFloat() * 2f - 1f) * (1f - age.toFloat() / (sampleRate / 70f))) * 0.035f
            } else {
                0f
            }
            if (released) releaseGain *= releaseMultiplier
            output[frame] += ((sample * level * attack * releaseGain) + hammer * level).toFloat()
            age++
        }
        return releaseGain > 0.0008 && amplitudes[0] > 0.00005f && age < sampleRate * 14
    }
}

internal class PluckedStringVoice(
    override val id: Long,
    midiNote: Int,
    velocity: Float,
    private val sampleRate: Int,
    private val bright: Boolean,
) : Voice {
    private val delay: FloatArray
    private var index = 0
    private var age = 0
    private var released = false
    private var releaseGain = 1f
    private var previous = 0f
    private val baseDamping: Float

    init {
        val frequency = NoteMath.midiToFrequency(midiNote)
        val length = max(2, (sampleRate / frequency).roundToInt())
        delay = FloatArray(length)
        val random = Random(id xor midiNote.toLong())
        val gain = velocity.coerceIn(0.05f, 1f) * 0.72f
        for (i in delay.indices) {
            val excitation = random.nextFloat() * 2f - 1f
            val pickShape = 0.65f + 0.35f * sin(PI * i / delay.size).toFloat()
            delay[i] = excitation * gain * pickShape
        }
        baseDamping = if (bright) 0.9968f else 0.9948f
    }

    override fun noteOff() {
        released = true
    }

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        repeat(frameCount) { frame ->
            val current = delay[index]
            val nextIndex = (index + 1) % delay.size
            val damping = if (released) 0.986f else baseDamping
            delay[index] = (current + delay[nextIndex]) * 0.5f * damping
            index = nextIndex

            previous = previous * 0.28f + current * 0.72f
            if (released) releaseGain *= 0.9992f
            output[frame] += previous * releaseGain * 0.58f
            age++
        }
        return releaseGain > 0.001f && age < sampleRate * if (bright) 10 else 8
    }
}

internal class BassVoice(
    override val id: Long,
    midiNote: Int,
    velocity: Float,
    private val sampleRate: Int,
) : Voice {
    private val increment = NoteMath.midiToFrequency(midiNote) / sampleRate
    private var phase = 0.0
    private var subPhase = 0.0
    private var envelope = 0f
    private var released = false
    private var filtered = 0f
    private var age = 0
    private val level = velocity.coerceIn(0.05f, 1f)
    private val filterCoefficient = (1.0 - exp(-2.0 * PI * 900.0 / sampleRate)).toFloat()

    override fun noteOff() {
        released = true
    }

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        repeat(frameCount) { frame ->
            envelope = when {
                released -> envelope * 0.9989f
                age < sampleRate / 125 -> (envelope + 1f / (sampleRate / 125f)).coerceAtMost(1f)
                else -> envelope + (0.72f - envelope) * 0.0015f
            }
            val saw = (2.0 * phase - 1.0).toFloat()
            val sub = sin(2.0 * PI * subPhase).toFloat()
            val raw = saw * 0.48f + sub * 0.52f
            filtered += filterCoefficient * (raw - filtered)
            output[frame] += filtered * envelope * level * 0.52f

            phase += increment
            subPhase += increment * 0.5
            if (phase >= 1.0) phase -= 1.0
            if (subPhase >= 1.0) subPhase -= 1.0
            age++
        }
        return !released || envelope > 0.001f
    }
}

internal class SynthVoice(
    override val id: Long,
    midiNote: Int,
    velocity: Float,
    private val waveform: Waveform,
    private val sampleRate: Int,
) : Voice {
    private val increment = NoteMath.midiToFrequency(midiNote) / sampleRate
    private val detunedIncrement = increment * 1.004
    private var phase = 0.0
    private var phase2 = 0.25
    private var envelope = 0f
    private var released = false
    private var age = 0
    private var filtered = 0f
    private val level = velocity.coerceIn(0.05f, 1f)
    private val filterCoefficient = (1.0 - exp(-2.0 * PI * 4200.0 / sampleRate)).toFloat()

    override fun noteOff() {
        released = true
    }

    private fun oscillator(value: Double): Float = when (waveform) {
        Waveform.SINE -> sin(2.0 * PI * value).toFloat()
        Waveform.SAW -> (2.0 * value - 1.0).toFloat()
        Waveform.SQUARE -> if (value < 0.5) 1f else -1f
        Waveform.TRIANGLE -> (1.0 - 4.0 * abs(value - 0.5)).toFloat()
    }

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        val attackIncrement = 1f / (sampleRate * 0.008f)
        repeat(frameCount) { frame ->
            envelope = when {
                released -> envelope * 0.9991f
                envelope < 0.99f && age < sampleRate / 20 -> (envelope + attackIncrement).coerceAtMost(1f)
                else -> envelope + (0.68f - envelope) * 0.0012f
            }
            val raw = oscillator(phase) * 0.58f + oscillator(phase2) * 0.42f
            filtered += filterCoefficient * (raw - filtered)
            output[frame] += filtered * envelope * level * 0.36f

            phase += increment
            phase2 += detunedIncrement
            if (phase >= 1.0) phase -= 1.0
            if (phase2 >= 1.0) phase2 -= 1.0
            age++
        }
        return !released || envelope > 0.001f
    }
}

internal class MalletVoice(
    override val id: Long,
    midiNote: Int,
    velocity: Float,
    private val sampleRate: Int,
) : Voice {
    private val phases = DoubleArray(5)
    private val increments = DoubleArray(5)
    private val amplitudes = floatArrayOf(1f, 0.48f, 0.27f, 0.15f, 0.08f)
    private val decays = DoubleArray(5)
    private var releaseGain = 1.0
    private var released = false
    private var age = 0
    private val level = velocity.coerceIn(0.05f, 1f) * 0.42f

    init {
        val frequency = NoteMath.midiToFrequency(midiNote)
        val modes = doubleArrayOf(1.0, 2.76, 5.41, 8.93, 13.34)
        val rates = doubleArrayOf(2.0, 3.6, 5.4, 7.2, 9.0)
        for (i in phases.indices) {
            increments[i] = 2.0 * PI * frequency * modes[i] / sampleRate
            decays[i] = exp(-rates[i] / sampleRate)
        }
    }

    override fun noteOff() {
        released = true
    }

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        val releaseMultiplier = exp(-1.0 / (sampleRate * 0.09))
        repeat(frameCount) { frame ->
            var sample = 0.0
            for (mode in phases.indices) {
                sample += sin(phases[mode]) * amplitudes[mode]
                phases[mode] += increments[mode]
                if (phases[mode] >= 2.0 * PI) phases[mode] -= 2.0 * PI
                amplitudes[mode] = (amplitudes[mode] * decays[mode]).toFloat()
            }
            if (released) releaseGain *= releaseMultiplier
            output[frame] += (sample * level * releaseGain).toFloat()
            age++
        }
        return releaseGain > 0.001 && amplitudes[0] > 0.0002f && age < sampleRate * 5
    }
}

internal class DrumVoice(
    override val id: Long,
    private val sound: Percussion,
    velocity: Float,
    private val sampleRate: Int,
) : Voice {
    private val random = Random(id xor sound.ordinal.toLong())
    private var sampleIndex = 0
    private var phase = 0.0
    private var phase2 = 0.0
    private var previousNoise = 0f
    private val level = velocity.coerceIn(0.05f, 1f)

    override fun noteOff() = Unit

    override fun render(output: FloatArray, frameCount: Int): Boolean {
        repeat(frameCount) { frame ->
            val t = sampleIndex.toDouble() / sampleRate
            val noise = random.nextFloat() * 2f - 1f
            val highNoise = noise - previousNoise
            previousNoise = noise
            val sample = when (sound) {
                Percussion.KICK -> {
                    val frequency = 43.0 + 105.0 * exp(-t * 22.0)
                    phase += 2.0 * PI * frequency / sampleRate
                    sin(phase).toFloat() * exp(-t * 9.0).toFloat()
                }
                Percussion.SNARE -> {
                    phase += 2.0 * PI * 185.0 / sampleRate
                    val body = sin(phase).toFloat() * 0.34f
                    (highNoise * 0.78f + body) * exp(-t * 13.0).toFloat()
                }
                Percussion.CLOSED_HAT ->
                    highNoise * exp(-t * 42.0).toFloat() * 0.74f
                Percussion.CLAP -> {
                    val burst = when {
                        t < 0.018 -> 1.0
                        t in 0.028..0.045 -> 0.84
                        t in 0.056..0.073 -> 0.68
                        t in 0.084..0.102 -> 0.52
                        else -> exp(-(t - 0.102).coerceAtLeast(0.0) * 16.0) * 0.32
                    }
                    highNoise * burst.toFloat()
                }
                Percussion.TOM -> {
                    val frequency = 105.0 + 95.0 * exp(-t * 16.0)
                    phase += 2.0 * PI * frequency / sampleRate
                    sin(phase).toFloat() * exp(-t * 8.0).toFloat()
                }
                Percussion.CYMBAL -> {
                    phase += 2.0 * PI * 657.0 / sampleRate
                    phase2 += 2.0 * PI * 941.0 / sampleRate
                    val metal = sin(phase).toFloat() * 0.22f + sin(phase2).toFloat() * 0.18f
                    (highNoise * 0.67f + metal) * exp(-t * 3.7).toFloat()
                }
                Percussion.METRONOME_LOW,
                Percussion.METRONOME_HIGH,
                -> {
                    val frequency = if (sound == Percussion.METRONOME_HIGH) 1760.0 else 1175.0
                    phase += 2.0 * PI * frequency / sampleRate
                    sin(phase).toFloat() * exp(-t * 48.0).toFloat()
                }
            }
            val scale = when (sound) {
                Percussion.KICK -> 0.78f
                Percussion.SNARE -> 0.48f
                Percussion.CLOSED_HAT -> 0.36f
                Percussion.CLAP -> 0.42f
                Percussion.TOM -> 0.62f
                Percussion.CYMBAL -> 0.34f
                Percussion.METRONOME_LOW,
                Percussion.METRONOME_HIGH,
                -> 0.34f
            }
            output[frame] += sample * level * scale
            sampleIndex++
        }
        val durationSeconds = when (sound) {
            Percussion.KICK -> 1.0
            Percussion.SNARE -> 0.8
            Percussion.CLOSED_HAT -> 0.28
            Percussion.CLAP -> 0.75
            Percussion.TOM -> 1.1
            Percussion.CYMBAL -> 3.0
            Percussion.METRONOME_LOW,
            Percussion.METRONOME_HIGH,
            -> 0.16
        }
        return sampleIndex < sampleRate * durationSeconds
    }
}
