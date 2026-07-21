package com.zoujiapeng.watchinstrument

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.zoujiapeng.watchinstrument.audio.AudioFocusController
import com.zoujiapeng.watchinstrument.audio.Metronome
import com.zoujiapeng.watchinstrument.audio.RealtimeAudioEngine
import com.zoujiapeng.watchinstrument.ui.MetronomeScreenView
import kotlin.math.roundToInt

class MetronomeActivity : BaseWatchActivity(), MetronomeScreenView.Listener {
    private lateinit var screenView: MetronomeScreenView
    private lateinit var audioEngine: RealtimeAudioEngine
    private lateinit var audioFocusController: AudioFocusController
    private lateinit var metronome: Metronome
    private val tapTimes = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = RealtimeAudioEngine(this).apply { setMasterVolume(appPreferences.masterVolume) }
        screenView = MetronomeScreenView(this, this).apply {
            bpm = appPreferences.defaultBpm
            beatsPerBar = appPreferences.beatsPerBar
            hapticsEnabled = appPreferences.hapticsEnabled
        }
        setContentView(screenView)

        metronome = Metronome(audioEngine) { beat, accented ->
            screenView.currentBeat = beat
            if (accented && appPreferences.hapticsEnabled) {
                screenView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }.apply {
            bpm = appPreferences.defaultBpm
            beatsPerBar = appPreferences.beatsPerBar
        }
        audioFocusController = AudioFocusController(this) {
            runOnUiThread {
                metronome.stop()
                screenView.isRunning = false
                screenView.currentBeat = -1
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audioEngine.setMasterVolume(appPreferences.masterVolume)
        if (audioEngine.start()) {
            audioFocusController.request()
        } else {
            Toast.makeText(this, R.string.audio_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        screenView.hapticsEnabled = appPreferences.hapticsEnabled
        audioEngine.setMasterVolume(appPreferences.masterVolume)
    }

    override fun onStop() {
        metronome.stop()
        screenView.isRunning = false
        screenView.currentBeat = -1
        audioEngine.stop()
        audioFocusController.abandon()
        super.onStop()
    }

    override fun onDestroy() {
        metronome.stop()
        audioEngine.stop()
        super.onDestroy()
    }

    override fun onBack() {
        finish()
    }

    override fun onToggle() {
        metronome.toggle()
        screenView.isRunning = metronome.isRunning
        if (!metronome.isRunning) screenView.currentBeat = -1
    }

    override fun onTempoChanged(bpm: Int) {
        appPreferences.defaultBpm = bpm
        metronome.bpm = bpm
        screenView.bpm = bpm
    }

    override fun onMeterChanged(beats: Int) {
        appPreferences.beatsPerBar = beats
        metronome.beatsPerBar = beats
        screenView.beatsPerBar = beats
    }

    override fun onTapTempo() {
        val now = SystemClock.elapsedRealtime()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2_000L) tapTimes.clear()
        tapTimes.addLast(now)
        while (tapTimes.size > 5) tapTimes.removeFirst()
        if (tapTimes.size >= 2) {
            val intervals = tapTimes.zipWithNext { a, b -> b - a }
            val average = intervals.average()
            if (average > 0.0) screenView.setTempoFromTap((60_000.0 / average).roundToInt())
        }
    }
}
