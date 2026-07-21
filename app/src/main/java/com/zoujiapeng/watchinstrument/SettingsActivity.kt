package com.zoujiapeng.watchinstrument

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.zoujiapeng.watchinstrument.audio.AudioFocusController
import com.zoujiapeng.watchinstrument.audio.RealtimeAudioEngine
import com.zoujiapeng.watchinstrument.audio.VoiceSpec
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.util.dp
import com.zoujiapeng.watchinstrument.util.roundedBackground
import com.zoujiapeng.watchinstrument.util.watchButton
import com.zoujiapeng.watchinstrument.util.watchText

class SettingsActivity : BaseWatchActivity() {
    private lateinit var audioEngine: RealtimeAudioEngine
    private lateinit var audioFocusController: AudioFocusController
    private val handler = Handler(Looper.getMainLooper())
    private val testVoices = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = RealtimeAudioEngine(this).apply { setMasterVolume(appPreferences.masterVolume) }
        audioFocusController = AudioFocusController(this) {
            runOnUiThread { audioEngine.allNotesOff() }
        }
        setContentView(buildContent())
    }

    override fun onStart() {
        super.onStart()
        if (audioEngine.start()) {
            audioFocusController.request()
        } else {
            Toast.makeText(this, R.string.audio_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        testVoices.forEach(audioEngine::noteOff)
        testVoices.clear()
        audioEngine.stop()
        audioFocusController.abandon()
        super.onStop()
    }

    override fun onDestroy() {
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
                LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        watchButton("‹") { finish() },
                        LinearLayout.LayoutParams(dp(44f), dp(44f)),
                    )
                    addView(
                        watchText(getString(R.string.settings), 17f, bold = true).apply {
                            gravity = Gravity.CENTER
                        },
                        LinearLayout.LayoutParams(0, dp(44f), 1f),
                    )
                    addView(TextView(this@SettingsActivity), LinearLayout.LayoutParams(dp(44f), dp(44f)))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)),
            )

            addView(
                ScrollView(this@SettingsActivity).apply {
                    isFillViewport = true
                    addView(
                        LinearLayout(this@SettingsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addVolumeControl(this)
                            addToggle(this, R.string.haptic_feedback, appPreferences.hapticsEnabled) {
                                appPreferences.hapticsEnabled = it
                            }
                            addToggle(this, R.string.show_note_labels, appPreferences.noteLabelsEnabled) {
                                appPreferences.noteLabelsEnabled = it
                            }
                            addToggle(this, R.string.keep_screen_on, appPreferences.keepScreenOn) {
                                appPreferences.keepScreenOn = it
                                updateKeepScreenOn()
                            }
                            addTempoControl(this)
                            addView(
                                watchButton(getString(R.string.audio_test)) { playAudioTest() },
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)).apply {
                                    topMargin = dp(8f)
                                },
                            )
                            addView(
                                watchText(getString(R.string.privacy_summary), 10f, color = Color.rgb(174, 181, 194)).apply {
                                    setPadding(dp(4f), dp(16f), dp(4f), dp(8f))
                                },
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                            )
                            addView(
                                watchText(getString(R.string.version_label, versionName()), 9f, color = Color.rgb(100, 106, 119)).apply {
                                    gravity = Gravity.CENTER
                                    setPadding(0, 0, 0, dp(14f))
                                },
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                            )
                        },
                        ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun addVolumeControl(parent: LinearLayout) {
        val card = settingCard()
        val label = watchText("${getString(R.string.master_volume)} · ${(appPreferences.masterVolume * 100).toInt()}%", 12f, bold = true)
        card.addView(label)
        card.addView(
            SeekBar(this).apply {
                max = 100
                progress = (appPreferences.masterVolume * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val volume = progress / 100f
                        appPreferences.masterVolume = volume
                        audioEngine.setMasterVolume(volume)
                        label.text = "${getString(R.string.master_volume)} · $progress%"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38f)),
        )
        parent.addView(card, cardParams())
    }

    @Suppress("DEPRECATION")
    private fun addToggle(
        parent: LinearLayout,
        labelRes: Int,
        initial: Boolean,
        onChanged: (Boolean) -> Unit,
    ) {
        val toggle = Switch(this).apply {
            text = getString(labelRes)
            textSize = 11.5f
            setTextColor(Color.WHITE)
            isChecked = initial
            setPadding(dp(2f), 0, dp(2f), 0)
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }
        parent.addView(
            toggle,
            cardParams(height = dp(50f)).apply {
                toggle.background = roundedBackground(Color.rgb(21, 23, 28), dp(13f).toFloat())
                toggle.setPadding(dp(12f), 0, dp(10f), 0)
            },
        )
    }

    private fun addTempoControl(parent: LinearLayout) {
        val card = settingCard()
        val label = watchText(getString(R.string.default_tempo, appPreferences.defaultBpm), 12f, bold = true)
        card.addView(label)
        card.addView(
            SeekBar(this).apply {
                max = 200
                progress = appPreferences.defaultBpm - 40
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val bpm = progress + 40
                        appPreferences.defaultBpm = bpm
                        label.text = getString(R.string.default_tempo, bpm)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38f)),
        )
        parent.addView(card, cardParams())
    }

    private fun settingCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12f), dp(9f), dp(12f), dp(6f))
        background = roundedBackground(Color.rgb(21, 23, 28), dp(13f).toFloat())
    }

    private fun cardParams(height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
            bottomMargin = dp(7f)
        }

    private fun playAudioTest() {
        testVoices.forEach(audioEngine::noteOff)
        testVoices.clear()
        val notes = intArrayOf(60, 64, 67, 72)
        notes.forEachIndexed { index, note ->
            handler.postDelayed({
                val voice = audioEngine.noteOn(
                    VoiceSpec(
                        instrument = InstrumentType.PIANO,
                        midiNote = note,
                        velocity = 0.78f,
                    ),
                )
                testVoices += voice
                handler.postDelayed({
                    audioEngine.noteOff(voice)
                    testVoices.remove(voice)
                }, 650L)
            }, index * 180L)
        }
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    }.getOrDefault("1.0.0")
}
