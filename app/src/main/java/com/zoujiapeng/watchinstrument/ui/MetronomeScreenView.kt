package com.zoujiapeng.watchinstrument.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.zoujiapeng.watchinstrument.R
import com.zoujiapeng.watchinstrument.util.dp
import kotlin.math.abs

class MetronomeScreenView(
    context: Context,
    private val listener: Listener,
) : View(context) {
    interface Listener {
        fun onBack()
        fun onToggle()
        fun onTempoChanged(bpm: Int)
        fun onMeterChanged(beats: Int)
        fun onTapTempo()
    }

    private enum class Action { BACK, MINUS, TOGGLE, PLUS, METER, TAP }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backRect = RectF()
    private val minusRect = RectF()
    private val toggleRect = RectF()
    private val plusRect = RectF()
    private val meterRect = RectF()
    private val tapRect = RectF()
    private var downAction: Action? = null

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
    var isRunning: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var currentBeat: Int = -1
        set(value) {
            field = value
            invalidate()
        }
    var hapticsEnabled: Boolean = true

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.metronome)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layoutRects()
        val edge = context.dp(10f).toFloat()

        drawButton(canvas, backRect, "‹", false, context.dp(23f).toFloat())
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        paint.textSize = context.dp(16f).toFloat()
        canvas.drawText(context.getString(R.string.metronome), width / 2f, edge + context.dp(26f), paint)

        val centerY = height * 0.38f
        paint.color = if (isRunning) Color.rgb(102, 224, 163) else Color.WHITE
        paint.textSize = context.dp(58f).toFloat()
        canvas.drawText(bpm.toString(), width / 2f, centerY, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = context.dp(11f).toFloat()
        paint.color = Color.rgb(174, 181, 194)
        canvas.drawText("BPM", width / 2f, centerY + context.dp(20f), paint)

        val dotGap = context.dp(24f).toFloat()
        val startX = width / 2f - (beatsPerBar - 1) * dotGap / 2f
        repeat(beatsPerBar) { index ->
            paint.color = if (index == currentBeat) Color.rgb(102, 224, 163) else Color.rgb(52, 58, 69)
            val radius = context.dp(if (index == currentBeat) 6f else 4f).toFloat()
            canvas.drawCircle(startX + index * dotGap, centerY + context.dp(50f), radius, paint)
        }

        drawButton(canvas, minusRect, "−", false, context.dp(24f).toFloat())
        drawButton(canvas, toggleRect, if (isRunning) "■" else "▶", isRunning, context.dp(22f).toFloat())
        drawButton(canvas, plusRect, "+", false, context.dp(22f).toFloat())
        drawButton(canvas, meterRect, "$beatsPerBar/4", false, context.dp(12f).toFloat())
        drawButton(canvas, tapRect, context.getString(R.string.tap_tempo), false, context.dp(11f).toFloat())

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(115, 122, 137)
        paint.textSize = context.dp(9f).toFloat()
        canvas.drawText(context.getString(R.string.crown_hint), width / 2f, height - context.dp(8f), paint)
    }

    private fun layoutRects() {
        val edge = context.dp(10f).toFloat()
        val headerTouch = context.dp(42f).toFloat()
        backRect.set(edge, edge, edge + headerTouch, edge + headerTouch)

        val rowGap = context.dp(6f).toFloat()
        val mainHeight = context.dp(58f).toFloat()
        val mainTop = height - edge - context.dp(118f)
        val available = width - edge * 2f - rowGap * 2f
        val mainWidth = available / 3f
        minusRect.set(edge, mainTop, edge + mainWidth, mainTop + mainHeight)
        toggleRect.set(minusRect.right + rowGap, mainTop, minusRect.right + rowGap + mainWidth, mainTop + mainHeight)
        plusRect.set(toggleRect.right + rowGap, mainTop, width - edge, mainTop + mainHeight)

        val secondTop = mainTop + mainHeight + rowGap
        val secondHeight = context.dp(42f).toFloat()
        val secondWidth = (width - edge * 2f - rowGap) / 2f
        meterRect.set(edge, secondTop, edge + secondWidth, secondTop + secondHeight)
        tapRect.set(meterRect.right + rowGap, secondTop, width - edge, secondTop + secondHeight)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, selected: Boolean, textSize: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (selected) Color.rgb(30, 61, 51) else Color.rgb(25, 28, 34)
        canvas.drawRoundRect(rect, context.dp(13f).toFloat(), context.dp(13f).toFloat(), paint)
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(1.2f).toFloat()
            paint.color = Color.rgb(102, 224, 163)
            canvas.drawRoundRect(rect, context.dp(13f).toFloat(), context.dp(13f).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = textSize
        paint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAction = actionAt(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = downAction
                downAction = null
                if (action != null && action == actionAt(event.x, event.y)) {
                    performClick()
                    perform(action)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downAction = null
                return true
            }
        }
        return true
    }

    private fun actionAt(x: Float, y: Float): Action? = when {
        backRect.contains(x, y) -> Action.BACK
        minusRect.contains(x, y) -> Action.MINUS
        toggleRect.contains(x, y) -> Action.TOGGLE
        plusRect.contains(x, y) -> Action.PLUS
        meterRect.contains(x, y) -> Action.METER
        tapRect.contains(x, y) -> Action.TAP
        else -> null
    }

    private fun perform(action: Action) {
        when (action) {
            Action.BACK -> listener.onBack()
            Action.MINUS -> changeTempo(-5)
            Action.TOGGLE -> listener.onToggle()
            Action.PLUS -> changeTempo(5)
            Action.METER -> {
                beatsPerBar = if (beatsPerBar >= 6) 2 else beatsPerBar + 1
                listener.onMeterChanged(beatsPerBar)
            }
            Action.TAP -> listener.onTapTempo()
        }
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun changeTempo(delta: Int) {
        val updated = (bpm + delta).coerceIn(40, 240)
        if (updated != bpm) {
            bpm = updated
            listener.onTempoChanged(updated)
        }
    }

    fun setTempoFromTap(value: Int) {
        val updated = value.coerceIn(40, 240)
        if (updated != bpm) {
            bpm = updated
            listener.onTempoChanged(updated)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (abs(delta) > 0.001f) {
                changeTempo(if (delta < 0f) 1 else -1)
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
}
