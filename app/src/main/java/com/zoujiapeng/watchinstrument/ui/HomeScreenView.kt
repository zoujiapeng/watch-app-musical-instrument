package com.zoujiapeng.watchinstrument.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.zoujiapeng.watchinstrument.R
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.util.dp
import kotlin.math.abs
import kotlin.math.min

class HomeScreenView(
    context: Context,
    private val listener: Listener,
) : View(context) {
    interface Listener {
        fun onInstrumentSelected(instrument: InstrumentType)
        fun onRecordingsSelected()
        fun onMetronomeSelected()
        fun onSettingsSelected()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cards = mutableListOf<Pair<InstrumentType, RectF>>()
    private val recordingsRect = RectF()
    private val metronomeRect = RectF()
    private val settingsRect = RectF()
    private var downX = 0f
    private var downY = 0f
    private var highlighted: InstrumentType? = null

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(R.string.home_title)
    }

    fun setHighlighted(instrument: InstrumentType) {
        highlighted = instrument
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val edge = context.dp(10f).toFloat()
        val width = width.toFloat()
        val height = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = context.dp(22f).toFloat()
        canvas.drawText(context.getString(R.string.home_title), edge + context.dp(2f), edge + context.dp(24f), paint)

        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = Color.rgb(174, 181, 194)
        paint.textSize = context.dp(11f).toFloat()
        canvas.drawText(context.getString(R.string.home_subtitle), edge + context.dp(2f), edge + context.dp(43f), paint)

        val gridTop = edge + context.dp(54f)
        val footerHeight = context.dp(52f).toFloat()
        val gridBottom = height - edge - footerHeight - context.dp(8f)
        val gap = context.dp(7f).toFloat()
        val cardWidth = (width - edge * 2f - gap) / 2f
        val cardHeight = (gridBottom - gridTop - gap * 2f) / 3f

        cards.clear()
        InstrumentType.entries.forEachIndexed { index, instrument ->
            val column = index % 2
            val row = index / 2
            val left = edge + column * (cardWidth + gap)
            val top = gridTop + row * (cardHeight + gap)
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)
            cards += instrument to rect
            drawCard(canvas, instrument, rect, instrument == highlighted)
        }

        val footerTop = height - edge - footerHeight
        val navGap = context.dp(5f).toFloat()
        val navWidth = (width - edge * 2f - navGap * 2f) / 3f
        recordingsRect.set(edge, footerTop, edge + navWidth, footerTop + footerHeight)
        metronomeRect.set(
            recordingsRect.right + navGap,
            footerTop,
            recordingsRect.right + navGap + navWidth,
            footerTop + footerHeight,
        )
        settingsRect.set(
            metronomeRect.right + navGap,
            footerTop,
            width - edge,
            footerTop + footerHeight,
        )
        drawNav(canvas, recordingsRect, context.getString(R.string.recordings), "●")
        drawNav(canvas, metronomeRect, context.getString(R.string.metronome), "⌁")
        drawNav(canvas, settingsRect, context.getString(R.string.settings), "⚙")
    }

    private fun drawCard(canvas: Canvas, instrument: InstrumentType, rect: RectF, selected: Boolean) {
        val radius = context.dp(13f).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = if (selected) Color.rgb(31, 54, 48) else Color.rgb(21, 23, 28)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(if (selected) 1.6f else 1f).toFloat()
        paint.color = if (selected) Color.rgb(102, 224, 163) else Color.rgb(43, 47, 57)
        canvas.drawRoundRect(rect, radius, radius, paint)

        val iconRect = RectF(
            rect.left + rect.width() * 0.09f,
            rect.top + rect.height() * 0.12f,
            rect.left + rect.width() * 0.46f,
            rect.top + rect.height() * 0.64f,
        )
        drawInstrumentIcon(canvas, instrument, iconRect)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = min(context.dp(14f).toFloat(), rect.height() * 0.17f)
        canvas.drawText(
            context.getString(instrument.titleRes),
            rect.right - rect.width() * 0.08f,
            rect.bottom - rect.height() * 0.16f,
            paint,
        )
    }

    private fun drawInstrumentIcon(canvas: Canvas, instrument: InstrumentType, rect: RectF) {
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = context.dp(2f).toFloat()
        paint.color = when (instrument) {
            InstrumentType.PIANO -> Color.rgb(101, 185, 255)
            InstrumentType.GUITAR -> Color.rgb(255, 180, 104)
            InstrumentType.DRUMS -> Color.rgb(255, 107, 122)
            InstrumentType.BASS -> Color.rgb(180, 139, 255)
            InstrumentType.XYLOPHONE -> Color.rgb(102, 224, 163)
            InstrumentType.SYNTH -> Color.rgb(255, 226, 110)
        }
        paint.style = Paint.Style.STROKE
        when (instrument) {
            InstrumentType.PIANO -> {
                canvas.drawRoundRect(rect, context.dp(4f).toFloat(), context.dp(4f).toFloat(), paint)
                val keyWidth = rect.width() / 5f
                repeat(4) { index ->
                    val x = rect.left + keyWidth * (index + 1)
                    canvas.drawLine(x, rect.top, x, rect.bottom, paint)
                }
                repeat(3) { index ->
                    val x = rect.left + keyWidth * (index + 1) - keyWidth * 0.17f
                    canvas.drawRect(x, rect.top, x + keyWidth * 0.34f, rect.centerY(), paint)
                }
            }
            InstrumentType.GUITAR,
            InstrumentType.BASS,
            -> {
                val strings = if (instrument == InstrumentType.GUITAR) 5 else 4
                repeat(strings) { index ->
                    val y = rect.top + rect.height() * (index + 1) / (strings + 1)
                    canvas.drawLine(rect.left, y, rect.right, y, paint)
                }
                repeat(3) { index ->
                    val x = rect.left + rect.width() * (index + 1) / 4f
                    canvas.drawLine(x, rect.top, x, rect.bottom, paint)
                }
            }
            InstrumentType.DRUMS -> {
                canvas.drawOval(RectF(rect.left, rect.top, rect.centerX(), rect.centerY()), paint)
                canvas.drawOval(RectF(rect.centerX(), rect.top, rect.right, rect.centerY()), paint)
                canvas.drawOval(RectF(rect.left + rect.width() * 0.2f, rect.centerY(), rect.right - rect.width() * 0.2f, rect.bottom), paint)
            }
            InstrumentType.XYLOPHONE -> {
                repeat(5) { index ->
                    val barWidth = rect.width() / 6f
                    val left = rect.left + index * (barWidth + rect.width() * 0.035f)
                    val inset = index * rect.height() * 0.035f
                    canvas.drawRoundRect(
                        RectF(left, rect.top + inset, left + barWidth, rect.bottom - inset),
                        barWidth * 0.25f,
                        barWidth * 0.25f,
                        paint,
                    )
                }
            }
            InstrumentType.SYNTH -> {
                val path = Path().apply {
                    moveTo(rect.left, rect.centerY())
                    cubicTo(
                        rect.left + rect.width() * 0.2f,
                        rect.top,
                        rect.left + rect.width() * 0.3f,
                        rect.bottom,
                        rect.left + rect.width() * 0.5f,
                        rect.centerY(),
                    )
                    cubicTo(
                        rect.left + rect.width() * 0.7f,
                        rect.top,
                        rect.left + rect.width() * 0.8f,
                        rect.bottom,
                        rect.right,
                        rect.centerY(),
                    )
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawNav(canvas: Canvas, rect: RectF, label: String, glyph: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(25, 28, 34)
        canvas.drawRoundRect(rect, context.dp(12f).toFloat(), context.dp(12f).toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.rgb(102, 224, 163)
        paint.textSize = context.dp(14f).toFloat()
        canvas.drawText(glyph, rect.centerX(), rect.top + rect.height() * 0.40f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = Color.WHITE
        paint.textSize = context.dp(9.5f).toFloat()
        canvas.drawText(label, rect.centerX(), rect.bottom - rect.height() * 0.18f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) > context.dp(12f) || abs(event.y - downY) > context.dp(12f)) {
                    return true
                }
                performClick()
                cards.firstOrNull { it.second.contains(event.x, event.y) }?.let {
                    listener.onInstrumentSelected(it.first)
                    return true
                }
                when {
                    recordingsRect.contains(event.x, event.y) -> listener.onRecordingsSelected()
                    metronomeRect.contains(event.x, event.y) -> listener.onMetronomeSelected()
                    settingsRect.contains(event.x, event.y) -> listener.onSettingsSelected()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
