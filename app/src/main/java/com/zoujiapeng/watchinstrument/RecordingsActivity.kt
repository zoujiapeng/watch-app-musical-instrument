package com.zoujiapeng.watchinstrument

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.zoujiapeng.watchinstrument.audio.AudioFocusController
import com.zoujiapeng.watchinstrument.audio.RealtimeAudioEngine
import com.zoujiapeng.watchinstrument.recording.PerformancePlayer
import com.zoujiapeng.watchinstrument.recording.PerformanceRecording
import com.zoujiapeng.watchinstrument.recording.RecordingRepository
import com.zoujiapeng.watchinstrument.util.dp
import com.zoujiapeng.watchinstrument.util.roundedBackground
import com.zoujiapeng.watchinstrument.util.watchButton
import com.zoujiapeng.watchinstrument.util.watchText
import java.text.DateFormat
import java.util.Date

class RecordingsActivity : BaseWatchActivity() {
    private lateinit var repository: RecordingRepository
    private lateinit var audioEngine: RealtimeAudioEngine
    private lateinit var audioFocusController: AudioFocusController
    private lateinit var player: PerformancePlayer
    private lateinit var listContainer: LinearLayout
    private var playingId: String? = null
    private val playButtons = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RecordingRepository(this)
        audioEngine = RealtimeAudioEngine(this).apply { setMasterVolume(appPreferences.masterVolume) }
        player = PerformancePlayer(audioEngine) {
            playingId = null
            refreshPlayButtons()
        }
        audioFocusController = AudioFocusController(this) {
            runOnUiThread {
                player.stop(notify = false)
                playingId = null
                refreshPlayButtons()
            }
        }
        setContentView(buildContent())
        rebuildList()
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
        rebuildList()
    }

    override fun onStop() {
        player.stop(notify = false)
        playingId = null
        audioEngine.stop()
        audioFocusController.abandon()
        super.onStop()
    }

    override fun onDestroy() {
        player.stop(notify = false)
        audioEngine.stop()
        super.onDestroy()
    }

    private fun buildContent(): LinearLayout {
        val edge = dp(10f)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(edge, edge, edge, edge)

            addView(
                LinearLayout(this@RecordingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        watchButton("‹") { finish() },
                        LinearLayout.LayoutParams(dp(44f), dp(44f)),
                    )
                    addView(
                        watchText(getString(R.string.recordings), 17f, bold = true).apply {
                            gravity = Gravity.CENTER
                        },
                        LinearLayout.LayoutParams(0, dp(44f), 1f),
                    )
                    addView(
                        TextView(this@RecordingsActivity),
                        LinearLayout.LayoutParams(dp(44f), dp(44f)),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)),
            )

            addView(
                ScrollView(this@RecordingsActivity).apply {
                    isFillViewport = true
                    listContainer = LinearLayout(this@RecordingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, dp(4f), 0, dp(8f))
                    }
                    addView(
                        listContainer,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun rebuildList() {
        if (!::listContainer.isInitialized) return
        val recordings = repository.list()
        listContainer.removeAllViews()
        playButtons.clear()
        if (recordings.isEmpty()) {
            listContainer.addView(
                watchText(getString(R.string.no_recordings), 12f, color = Color.rgb(174, 181, 194)).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(18f), dp(42f), dp(18f), dp(18f))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            return
        }
        recordings.forEach { listContainer.addView(recordingCard(it)) }
        refreshPlayButtons()
    }

    private fun recordingCard(recording: PerformanceRecording): LinearLayout {
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(recording.createdAtEpochMs))
        val duration = getString(R.string.duration_seconds, recording.durationMs / 1000.0)
        val title = getString(R.string.saved_take_title, getString(recording.instrument.titleRes))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = roundedBackground(Color.rgb(21, 23, 28), dp(13f).toFloat())

            addView(
                watchText(title, 13f, bold = true),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                watchText("$date · $duration", 9.5f, color = Color.rgb(174, 181, 194)).apply {
                    setPadding(0, dp(4f), 0, dp(8f))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            addView(
                LinearLayout(this@RecordingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val playButton = watchButton(getString(R.string.play)) {
                        togglePlayback(recording)
                    }
                    playButtons[recording.id] = playButton
                    addView(
                        playButton,
                        LinearLayout.LayoutParams(0, dp(40f), 1f).apply { marginEnd = dp(6f) },
                    )
                    addView(
                        watchButton(getString(R.string.delete), danger = true) { confirmDelete(recording) },
                        LinearLayout.LayoutParams(0, dp(40f), 1f),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)),
            )
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(7f) }
            card.contentDescription = getString(R.string.recording_item_description, title, duration)
        }
    }

    private fun togglePlayback(recording: PerformanceRecording) {
        if (player.isPlaying && playingId == recording.id) {
            player.stop(notify = false)
            playingId = null
        } else {
            player.stop(notify = false)
            playingId = recording.id
            player.play(recording)
        }
        refreshPlayButtons()
    }

    private fun refreshPlayButtons() {
        playButtons.forEach { (id, button) ->
            button.text = if (id == playingId && player.isPlaying) getString(R.string.stop) else getString(R.string.play)
        }
    }

    private fun confirmDelete(recording: PerformanceRecording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recording_title)
            .setMessage(R.string.delete_recording_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (playingId == recording.id) {
                    player.stop(notify = false)
                    playingId = null
                }
                repository.delete(recording.id)
                rebuildList()
            }
            .show()
    }
}
