package com.zoujiapeng.watchinstrument

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.zoujiapeng.watchinstrument.audio.AudioFocusController
import com.zoujiapeng.watchinstrument.audio.Metronome
import com.zoujiapeng.watchinstrument.audio.RealtimeAudioEngine
import com.zoujiapeng.watchinstrument.audio.VoiceSpec
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.PlayableControl
import com.zoujiapeng.watchinstrument.model.Waveform
import com.zoujiapeng.watchinstrument.recording.PerformancePlayer
import com.zoujiapeng.watchinstrument.recording.PerformanceRecorder
import com.zoujiapeng.watchinstrument.recording.PerformanceRecording
import com.zoujiapeng.watchinstrument.recording.RecordingRepository
import com.zoujiapeng.watchinstrument.ui.InstrumentScreenView

class InstrumentActivity : BaseWatchActivity(), InstrumentScreenView.Listener {
    private lateinit var instrument: InstrumentType
    private lateinit var screenView: InstrumentScreenView
    private lateinit var audioEngine: RealtimeAudioEngine
    private lateinit var audioFocusController: AudioFocusController
    private lateinit var metronome: Metronome
    private lateinit var player: PerformancePlayer
    private lateinit var repository: RecordingRepository
    private val recorder = PerformanceRecorder()
    private var lastRecording: PerformanceRecording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instrument = InstrumentType.fromId(intent.getStringExtra(EXTRA_INSTRUMENT))
        appPreferences.lastInstrument = instrument

        audioEngine = RealtimeAudioEngine(this).apply {
            setMasterVolume(appPreferences.masterVolume)
        }
        audioFocusController = AudioFocusController(this) {
            runOnUiThread {
                screenView.releaseAll()
                metronome.stop()
                screenView.isMetronomeRunning = false
                player.stop(notify = false)
                screenView.isLastTakePlaying = false
            }
        }
        repository = RecordingRepository(this)
        lastRecording = repository.list().firstOrNull { it.instrument == instrument }

        screenView = InstrumentScreenView(
            context = this,
            instrument = instrument,
            initialOctave = appPreferences.octave(instrument),
            initialWaveform = appPreferences.waveform,
            listener = this,
        ).apply {
            showNoteLabels = appPreferences.noteLabelsEnabled
            hapticsEnabled = appPreferences.hapticsEnabled
            bpm = appPreferences.defaultBpm
            beatsPerBar = appPreferences.beatsPerBar
            hasLastTake = lastRecording != null
        }
        setContentView(screenView)

        player = PerformancePlayer(audioEngine) {
            screenView.isLastTakePlaying = false
        }
        metronome = Metronome(audioEngine) { beat, accented ->
            screenView.currentBeat = beat
            if (accented && appPreferences.hapticsEnabled) {
                screenView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }.apply {
            bpm = appPreferences.defaultBpm
            beatsPerBar = appPreferences.beatsPerBar
        }
    }

    override fun onStart() {
        super.onStart()
        audioEngine.setMasterVolume(appPreferences.masterVolume)
        if (!audioEngine.start()) {
            Toast.makeText(this, R.string.audio_unavailable, Toast.LENGTH_LONG).show()
        } else {
            audioFocusController.request()
        }
    }

    override fun onResume() {
        super.onResume()
        screenView.showNoteLabels = appPreferences.noteLabelsEnabled
        screenView.hapticsEnabled = appPreferences.hapticsEnabled
        screenView.bpm = appPreferences.defaultBpm
        screenView.beatsPerBar = appPreferences.beatsPerBar
        audioEngine.setMasterVolume(appPreferences.masterVolume)
    }

    override fun onStop() {
        if (recorder.isRecording) finishRecording(showMessage = false)
        screenView.releaseAll()
        metronome.stop()
        screenView.isMetronomeRunning = false
        player.stop(notify = false)
        screenView.isLastTakePlaying = false
        audioEngine.stop()
        audioFocusController.abandon()
        super.onStop()
    }

    override fun onDestroy() {
        recorder.cancel()
        metronome.stop()
        player.stop(notify = false)
        audioEngine.stop()
        super.onDestroy()
    }

    override fun onBackRequested() {
        finish()
    }

    override fun onNoteOn(control: PlayableControl, waveform: Waveform, velocity: Float): Long {
        val voiceId = audioEngine.noteOn(
            VoiceSpec(
                instrument = instrument,
                midiNote = control.midiNote ?: 60,
                percussion = control.percussion,
                waveform = waveform,
                velocity = velocity,
            ),
        )
        recorder.noteOn(voiceId, control, waveform, velocity)
        return voiceId
    }

    override fun onNoteOff(voiceId: Long) {
        audioEngine.noteOff(voiceId)
        recorder.noteOff(voiceId)
    }

    override fun onRecordToggle() {
        if (recorder.isRecording) {
            finishRecording(showMessage = true)
        } else {
            player.stop(notify = false)
            screenView.isLastTakePlaying = false
            recorder.start(instrument)
            screenView.isRecording = true
            Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishRecording(showMessage: Boolean) {
        val recording = recorder.stop()
        screenView.isRecording = false
        if (recording == null) {
            if (showMessage) Toast.makeText(this, R.string.recording_empty, Toast.LENGTH_SHORT).show()
            return
        }
        repository.save(recording)
        lastRecording = recording
        screenView.hasLastTake = true
        if (showMessage) Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onMetronomeToggle() {
        metronome.toggle()
        screenView.isMetronomeRunning = metronome.isRunning
        if (!metronome.isRunning) screenView.currentBeat = -1
    }

    override fun onPlayLastToggle() {
        if (player.isPlaying) {
            player.stop()
            screenView.isLastTakePlaying = false
            return
        }
        val recording = lastRecording ?: return
        screenView.releaseAll()
        player.play(recording)
        screenView.isLastTakePlaying = true
    }

    override fun onOctaveChanged(octave: Int) {
        appPreferences.setOctave(instrument, octave)
    }

    override fun onWaveformChanged(waveform: Waveform) {
        appPreferences.waveform = waveform
    }

    override fun onTempoChanged(bpm: Int) {
        appPreferences.defaultBpm = bpm
        metronome.bpm = bpm
        screenView.bpm = bpm
    }

    override fun onMeterChanged(beatsPerBar: Int) {
        appPreferences.beatsPerBar = beatsPerBar
        metronome.beatsPerBar = beatsPerBar
        screenView.beatsPerBar = beatsPerBar
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        screenView.releaseAll()
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_INSTRUMENT = "instrument"
    }
}
