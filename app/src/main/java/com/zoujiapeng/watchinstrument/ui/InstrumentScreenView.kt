package com.zoujiapeng.watchinstrument.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.zoujiapeng.watchinstrument.R
import com.zoujiapeng.watchinstrument.model.InstrumentLayouts
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.PlayableControl
import com.zoujiapeng.watchinstrument.model.Waveform
import com.zoujiapeng.watchinstrument.settings.AppPreferences
import com.zoujiapeng.watchinstrument.util.dp
import kotlin.math.abs
import kotlin.math.min

class InstrumentScreenView(
    context: Context,
    val instrument: InstrumentType,
    initialOctave: Int,
    initialWaveform: Waveform,
    private val listener: Listener,
) : View(context) {
    interface Listener {
        fun onBackRequested()
        fun onNoteOn(control: PlayableControl, waveform: Waveform, velocity: Float): Long
        fun onNoteOff(voiceId: Long)
        fun onRecordToggle()
        fun onMetronomeToggle()
        fun onPlayLastToggle()
        fun onOctaveChanged(octave: Int)
        fun onWaveformChanged(waveform: Waveform)
        fun onTempoChanged(bpm: Int)
        fun onMeterChanged(beatsPerBar: Int)
    }

    private sealed interface PointerTarget {
        data class Note(val control: PlayableControl, val voiceId: Long) : PointerTarget
        data class Button(val action: ButtonAction) : PointerTarget
    }

    private enum class ButtonAction {
        BACK,
        RECORD,
        METRONOME,
        DECREASE,
        OPTION,
        INCREASE,
        PLAY_LAST,
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerRect = RectF()
    private val playableRect = RectF()
    private val footerRect = RectF()
    private val backRect = RectF()
    private val recordRect = RectF()
    private val metronomeRect = RectF()
    private val footerButtons = Array(4) { RectF() }
    private val pointerTargets = SparseArray<PointerTarget>()
    private val sustainedVoices = linkedMapOf<Long, Int>()

    private var octave = initialOctave.coerceIn(AppPreferences.octaveRange(instrument))
    private var waveform = initialWaveform
    private var controls = InstrumentLayouts.controls(instrument, octave)
    private var sustainEnabled = false

    var showNoteLabels: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var hapticsEnabled: Boolean = true
    var isRecording: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isMetronomeRunning: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var currentBeat: Int = -1
        set(value) {
            field = value
            invalidate()
        }
    var hasLastTake: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isLastTakePlaying: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var bpm: Int = 100
        set(value) {
            field = value.coerceIn(40, 240)
            invalidate()
        }
    var beatsPerBar: Int = 4
        set(value) {
            field = value.coerceIn(2, 6)
            invalidate()
        }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(instrument.titleRes)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layoutRegions()
        drawHeader(canvas)
        drawInstrument(canvas)
        drawFooter(canvas)
    }

    private fun layoutRegions() {
        val edge = context.dp(8f).toFloat()
        val headerHeight = context.dp(50f).toFloat()
        val footerHeight = context.dp(57f).toFloat()
        val gap = context.dp(5f).toFloat()

        headerRect.set(edge, edge, width - edge, edge + headerHeight)
        footerRect.set(edge, height - edge - footerHeight, width - edge, height - edge)
        playableRect.set(edge, headerRect.bottom + gap, width - edge, footerRect.top - gap)

        val touch = context.dp(42f).toFloat()
        backRect.set(headerRect.left, headerRect.top, headerRect.left + touch, headerRect.bottom)
        recordRect.set(headerRect.right - touch, headerRect.top, headerRect.right, headerRect.bottom)
        metronomeRect.set(recordRect.left - touch - context.dp(3f), headerRect.top, recordRect.left - context.dp(3f), headerRect.bottom)

        val buttonGap = context.dp(4f).toFloat()
        val buttonWidth = (footerRect.width() - buttonGap * 3f) / 4f
        repeat(4) { index ->
            val left = footerRect.left + index * (buttonWidth + buttonGap)
            footerButtons[index].set(left, footerRect.top, left + buttonWidth, footerRect.bottom)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        drawButtonBackground(canvas, backRect, selected = false)
        drawButtonBackground(canvas, metronomeRect, selected = isMetronomeRunning)
        drawButtonBackground(canvas, recordRect, selected = isRecording, danger = true)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        paint.textSize = context.dp(24f).toFloat()
        canvas.drawText("‹", backRect.centerX(), backRect.centerY() + context.dp(8f), paint)

        paint.textSize = context.dp(10f).toFloat()
        paint.color = if (isMetronomeRunning) Color.rgb(102, 224, 163) else Color.WHITE
        canvas.drawText("MET", metronomeRect.centerX(), metronomeRect.centerY() + context.dp(3f), paint)

        paint.color = if (isRecording) Color.rgb(255, 107, 122) else Color.WHITE
        paint.textSize = context.dp(17f).toFloat()
        canvas.drawText(if (isRecording) "■" else "●", recordRect.centerX(), recordRect.centerY() + context.dp(5f), paint)

        val titleLeft = backRect.right + context.dp(3f)
        val titleRight = metronomeRect.left - context.dp(3f)
        val titleCenter = (titleLeft + titleRight) / 2f
        paint.color = Color.WHITE
        paint.textSize = context.dp(15f).toFloat()
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(context.getString(instrument.titleRes), titleCenter, headerRect.top + context.dp(21f), paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = context.dp(8.5f).toFloat()
        paint.color = Color.rgb(174, 181, 194)
        val status = if (instrument == InstrumentType.DRUMS) {
            context.getString(R.string.tempo_bpm, bpm)
        } else {
            context.getString(R.string.octave, octave)
        }
        canvas.drawText(status, titleCenter, headerRect.bottom - context.dp(8f), paint)

        if (isMetronomeRunning) {
            val dotGap = context.dp(6f).toFloat()
            val startX = titleCenter - (beatsPerBar - 1) * dotGap / 2f
            repeat(beatsPerBar) { index ->
                paint.color = if (index == currentBeat) Color.rgb(102, 224, 163) else Color.rgb(70, 76, 89)
                canvas.drawCircle(startX + index * dotGap, headerRect.bottom - context.dp(1.5f), context.dp(1.5f).toFloat(), paint)
            }
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val labels = footerLabels()
        footerButtons.forEachIndexed { index, rect ->
            val selected = when (index) {
                1 -> (instrument == InstrumentType.PIANO && sustainEnabled)
                3 -> isLastTakePlaying
                else -> false
            }
            drawButtonBackground(canvas, rect, selected = selected)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = if (index == 1) context.dp(10f).toFloat() else context.dp(17f).toFloat()
            paint.color = if (index == 3 && !hasLastTake) Color.rgb(82, 87, 98) else Color.WHITE
            canvas.drawText(labels[index], rect.centerX(), rect.centerY() + context.dp(5f), paint)
        }
    }

    private fun footerLabels(): Array<String> = arrayOf(
        "−",
        when (instrument) {
            InstrumentType.PIANO -> if (sustainEnabled) "SUS ON" else "SUS"
            InstrumentType.SYNTH -> waveform.name.take(3)
            InstrumentType.DRUMS -> "$beatsPerBar/4"
            else -> "OCT $octave"
        },
        "+",
        if (isLastTakePlaying) "■" else "▶",
    )

    private fun drawButtonBackground(
        canvas: Canvas,
        rect: RectF,
        selected: Boolean,
        danger: Boolean = false,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = when {
            danger && selected -> Color.rgb(78, 31, 40)
            selected -> Color.rgb(30, 61, 51)
            else -> Color.rgb(25, 28, 34)
        }
        val radius = context.dp(12f).toFloat()
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(1.2f).toFloat()
            paint.color = if (danger) Color.rgb(255, 107, 122) else Color.rgb(102, 224, 163)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    private fun drawInstrument(canvas: Canvas) {
        when (instrument) {
            InstrumentType.PIANO -> drawPiano(canvas)
            InstrumentType.GUITAR -> drawStrings(canvas, guitar = true)
            InstrumentType.DRUMS -> drawDrums(canvas)
            InstrumentType.BASS -> drawStrings(canvas, guitar = false)
            InstrumentType.XYLOPHONE -> drawXylophone(canvas)
            InstrumentType.SYNTH -> drawSynth(canvas)
        }
    }

    private fun drawPiano(canvas: Canvas) {
        val active = activeControlIds()
        paint.strokeWidth = context.dp(1f).toFloat()
        controls.filter { it.zIndex == 0 }.forEach { control ->
            val rect = absoluteRect(control)
            paint.style = Paint.Style.FILL
            paint.color = if (control.id in active) Color.rgb(145, 210, 255) else Color.rgb(238, 240, 244)
            canvas.drawRoundRect(rect, context.dp(3f).toFloat(), context.dp(3f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(52, 57, 67)
            canvas.drawRoundRect(rect, context.dp(3f).toFloat(), context.dp(3f).toFloat(), paint)
            if (showNoteLabels) {
                drawCenteredLabel(canvas, control.label, rect.centerX(), rect.bottom - context.dp(10f), Color.rgb(25, 28, 34), context.dp(8f).toFloat())
            }
        }
        controls.filter { it.zIndex > 0 }.forEach { control ->
            val rect = absoluteRect(control)
            paint.style = Paint.Style.FILL
            paint.color = if (control.id in active) Color.rgb(102, 224, 163) else Color.rgb(12, 13, 16)
            canvas.drawRoundRect(rect, context.dp(4f).toFloat(), context.dp(4f).toFloat(), paint)
            if (showNoteLabels) {
                drawCenteredLabel(canvas, control.label, rect.centerX(), rect.bottom - context.dp(8f), Color.WHITE, context.dp(7f).toFloat())
            }
        }
    }

    private fun drawStrings(canvas: Canvas, guitar: Boolean) {
        val active = activeControlIds()
        paint.style = Paint.Style.FILL
        paint.color = if (guitar) Color.rgb(50, 31, 22) else Color.rgb(28, 25, 48)
        canvas.drawRoundRect(playableRect, context.dp(12f).toFloat(), context.dp(12f).toFloat(), paint)

        val rows = if (guitar) 6 else 4
        val columns = if (guitar) 6 else 5
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(128, 111, 92)
        repeat(columns + 1) { index ->
            val x = playableRect.left + playableRect.width() * index / columns
            paint.strokeWidth = if (index == 0) context.dp(3f).toFloat() else context.dp(1f).toFloat()
            canvas.drawLine(x, playableRect.top, x, playableRect.bottom, paint)
        }
        repeat(rows) { row ->
            val y = playableRect.top + playableRect.height() * (row + 0.5f) / rows
            paint.strokeWidth = context.dp(0.8f + row * 0.18f).toFloat()
            paint.color = if (guitar) Color.rgb(224, 206, 174) else Color.rgb(180, 175, 230)
            canvas.drawLine(playableRect.left, y, playableRect.right, y, paint)
        }

        controls.forEach { control ->
            val rect = absoluteRect(control)
            if (control.id in active) {
                paint.style = Paint.Style.FILL
                paint.color = if (guitar) Color.rgb(111, 74, 43) else Color.rgb(73, 58, 125)
                canvas.drawRoundRect(rect, context.dp(7f).toFloat(), context.dp(7f).toFloat(), paint)
            }
            if (showNoteLabels && (control.id in active || rect.width() >= context.dp(45f))) {
                drawCenteredLabel(
                    canvas,
                    control.label,
                    rect.centerX(),
                    rect.centerY() + context.dp(3f),
                    Color.WHITE,
                    min(context.dp(8f).toFloat(), rect.height() * 0.20f),
                )
            }
        }
    }

    private fun drawDrums(canvas: Canvas) {
        val active = activeControlIds()
        val colors = intArrayOf(
            Color.rgb(76, 135, 255),
            Color.rgb(255, 95, 112),
            Color.rgb(255, 207, 92),
            Color.rgb(179, 117, 255),
            Color.rgb(102, 224, 163),
            Color.rgb(255, 145, 82),
        )
        controls.forEach { control ->
            val rect = absoluteRect(control)
            paint.style = Paint.Style.FILL
            paint.color = if (control.id in active) lighten(colors[control.id], 0.24f) else colors[control.id]
            canvas.drawRoundRect(rect, context.dp(15f).toFloat(), context.dp(15f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(if (control.id in active) 2f else 1f).toFloat()
            paint.color = if (control.id in active) Color.WHITE else Color.argb(110, 255, 255, 255)
            canvas.drawRoundRect(rect, context.dp(15f).toFloat(), context.dp(15f).toFloat(), paint)
            drawCenteredLabel(canvas, control.label, rect.centerX(), rect.centerY() + context.dp(4f), Color.WHITE, context.dp(11f).toFloat())
        }
    }

    private fun drawXylophone(canvas: Canvas) {
        val active = activeControlIds()
        val colors = intArrayOf(
            Color.rgb(255, 100, 110),
            Color.rgb(255, 154, 85),
            Color.rgb(255, 215, 88),
            Color.rgb(107, 222, 158),
            Color.rgb(84, 190, 225),
            Color.rgb(95, 135, 255),
            Color.rgb(169, 112, 245),
            Color.rgb(231, 110, 207),
        )
        controls.forEach { control ->
            val rect = absoluteRect(control)
            paint.style = Paint.Style.FILL
            paint.color = if (control.id in active) lighten(colors[control.id], 0.22f) else colors[control.id]
            canvas.drawRoundRect(rect, rect.width() * 0.28f, rect.width() * 0.28f, paint)
            if (showNoteLabels) {
                canvas.save()
                canvas.rotate(-90f, rect.centerX(), rect.centerY())
                drawCenteredLabel(canvas, control.label, rect.centerX(), rect.centerY() + context.dp(3f), Color.rgb(20, 22, 27), context.dp(8f).toFloat())
                canvas.restore()
            }
        }
    }

    private fun drawSynth(canvas: Canvas) {
        val active = activeControlIds()
        controls.forEach { control ->
            val rect = absoluteRect(control)
            paint.style = Paint.Style.FILL
            paint.color = if (control.id in active) Color.rgb(102, 224, 163) else Color.rgb(30, 39, 48)
            canvas.drawRoundRect(rect, context.dp(13f).toFloat(), context.dp(13f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(1f).toFloat()
            paint.color = if (control.id in active) Color.WHITE else Color.rgb(80, 98, 113)
            canvas.drawRoundRect(rect, context.dp(13f).toFloat(), context.dp(13f).toFloat(), paint)
            if (showNoteLabels) {
                drawCenteredLabel(canvas, control.label, rect.centerX(), rect.centerY() + context.dp(4f), Color.WHITE, context.dp(10f).toFloat())
            }
        }
    }

    private fun drawCenteredLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        color: Int,
        size: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = color
        paint.textSize = size
        canvas.drawText(text, x, baseline, paint)
    }

    private fun absoluteRect(control: PlayableControl): RectF = RectF(
        playableRect.left + control.region.left * playableRect.width(),
        playableRect.top + control.region.top * playableRect.height(),
        playableRect.left + control.region.right * playableRect.width(),
        playableRect.top + control.region.bottom * playableRect.height(),
    )

    private fun activeControlIds(): Set<Int> = buildSet {
        for (index in 0 until pointerTargets.size()) {
            val target = pointerTargets.valueAt(index)
            if (target is PointerTarget.Note) add(target.control.id)
        }
        addAll(sustainedVoices.values)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> handlePointerDown(event, event.actionIndex)

            MotionEvent.ACTION_MOVE -> handlePointerMove(event)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            -> handlePointerUp(event, event.actionIndex)

            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                pointerTargets.clear()
            }
        }
        return true
    }

    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val button = buttonAt(x, y)
        if (button != null) {
            pointerTargets.put(pointerId, PointerTarget.Button(button))
            return
        }
        if (!playableRect.contains(x, y)) return
        val control = hitControl(x, y) ?: return
        pointerTargets.put(pointerId, startControl(control, event.getPressure(pointerIndex)))
    }

    private fun handlePointerMove(event: MotionEvent) {
        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            val target = pointerTargets[pointerId]
            if (target !is PointerTarget.Note) continue
            val x = event.getX(index)
            val y = event.getY(index)
            val nextControl = if (playableRect.contains(x, y)) hitControl(x, y) else null
            if (nextControl?.id == target.control.id) continue

            releaseNote(target, allowSustain = true)
            if (nextControl == null) {
                pointerTargets.remove(pointerId)
            } else {
                pointerTargets.put(pointerId, startControl(nextControl, event.getPressure(index)))
            }
        }
        invalidate()
    }

    private fun handlePointerUp(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        when (val target = pointerTargets[pointerId]) {
            is PointerTarget.Note -> releaseNote(target, allowSustain = true)
            is PointerTarget.Button -> {
                val currentButton = buttonAt(event.getX(pointerIndex), event.getY(pointerIndex))
                if (currentButton == target.action) {
                    performClick()
                    performButton(target.action)
                }
            }
            null -> Unit
        }
        pointerTargets.remove(pointerId)
        invalidate()
    }

    private fun startControl(control: PlayableControl, pressure: Float): PointerTarget.Note {
        val velocity = (0.58f + pressure.coerceIn(0f, 1f) * 0.42f).coerceIn(0.05f, 1f)
        val voiceId = listener.onNoteOn(control, waveform, velocity)
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
        return PointerTarget.Note(control, voiceId)
    }

    private fun releaseNote(target: PointerTarget.Note, allowSustain: Boolean) {
        if (allowSustain && instrument == InstrumentType.PIANO && sustainEnabled) {
            sustainedVoices[target.voiceId] = target.control.id
        } else {
            listener.onNoteOff(target.voiceId)
        }
    }

    private fun hitControl(x: Float, y: Float): PlayableControl? {
        if (playableRect.width() <= 0f || playableRect.height() <= 0f) return null
        val normalizedX = ((x - playableRect.left) / playableRect.width()).coerceIn(0f, 1f)
        val normalizedY = ((y - playableRect.top) / playableRect.height()).coerceIn(0f, 1f)
        return InstrumentLayouts.hitTest(controls, normalizedX, normalizedY)
    }

    private fun buttonAt(x: Float, y: Float): ButtonAction? = when {
        backRect.contains(x, y) -> ButtonAction.BACK
        recordRect.contains(x, y) -> ButtonAction.RECORD
        metronomeRect.contains(x, y) -> ButtonAction.METRONOME
        footerButtons[0].contains(x, y) -> ButtonAction.DECREASE
        footerButtons[1].contains(x, y) -> ButtonAction.OPTION
        footerButtons[2].contains(x, y) -> ButtonAction.INCREASE
        footerButtons[3].contains(x, y) -> ButtonAction.PLAY_LAST
        else -> null
    }

    private fun performButton(action: ButtonAction) {
        when (action) {
            ButtonAction.BACK -> {
                releaseAll()
                listener.onBackRequested()
            }
            ButtonAction.RECORD -> listener.onRecordToggle()
            ButtonAction.METRONOME -> listener.onMetronomeToggle()
            ButtonAction.DECREASE -> if (instrument == InstrumentType.DRUMS) adjustTempo(-5) else adjustOctave(-1)
            ButtonAction.INCREASE -> if (instrument == InstrumentType.DRUMS) adjustTempo(5) else adjustOctave(1)
            ButtonAction.OPTION -> when (instrument) {
                InstrumentType.PIANO -> toggleSustain()
                InstrumentType.SYNTH -> {
                    waveform = waveform.next()
                    listener.onWaveformChanged(waveform)
                }
                InstrumentType.DRUMS -> {
                    beatsPerBar = if (beatsPerBar >= 6) 2 else beatsPerBar + 1
                    listener.onMeterChanged(beatsPerBar)
                }
                else -> Unit
            }
            ButtonAction.PLAY_LAST -> if (hasLastTake) listener.onPlayLastToggle()
        }
        invalidate()
    }

    private fun adjustOctave(delta: Int) {
        if (!instrument.supportsOctave) return
        val updated = (octave + delta).coerceIn(AppPreferences.octaveRange(instrument))
        if (updated == octave) return
        releaseAll()
        octave = updated
        controls = InstrumentLayouts.controls(instrument, octave)
        listener.onOctaveChanged(octave)
        invalidate()
    }

    private fun adjustTempo(delta: Int) {
        val updated = (bpm + delta).coerceIn(40, 240)
        if (updated == bpm) return
        bpm = updated
        listener.onTempoChanged(updated)
    }

    private fun toggleSustain() {
        sustainEnabled = !sustainEnabled
        if (!sustainEnabled) {
            sustainedVoices.keys.toList().forEach(listener::onNoteOff)
            sustainedVoices.clear()
        }
        invalidate()
    }

    fun releaseAll() {
        for (index in 0 until pointerTargets.size()) {
            val target = pointerTargets.valueAt(index)
            if (target is PointerTarget.Note) listener.onNoteOff(target.voiceId)
        }
        sustainedVoices.keys.toList().forEach(listener::onNoteOff)
        sustainedVoices.clear()
        pointerTargets.clear()
        sustainEnabled = false
        invalidate()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (abs(delta) > 0.001f) {
                val direction = if (delta < 0f) 1 else -1
                if (instrument == InstrumentType.DRUMS) adjustTempo(direction) else adjustOctave(direction)
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun lighten(color: Int, amount: Float): Int {
        val factor = amount.coerceIn(0f, 1f)
        val red = (Color.red(color) + (255 - Color.red(color)) * factor).toInt()
        val green = (Color.green(color) + (255 - Color.green(color)) * factor).toInt()
        val blue = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt()
        return Color.rgb(red, green, blue)
    }
}
