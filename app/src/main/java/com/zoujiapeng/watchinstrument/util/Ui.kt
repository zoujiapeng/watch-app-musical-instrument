package com.zoujiapeng.watchinstrument.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

fun roundedBackground(color: Int, radiusPx: Float, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusPx
        if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun Context.watchText(
    text: CharSequence,
    sizeSp: Float = 13f,
    bold: Boolean = false,
    color: Int = Color.WHITE,
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    includeFontPadding = false
}

fun Context.watchButton(
    text: CharSequence,
    danger: Boolean = false,
    onClick: (View) -> Unit,
): Button = Button(this).apply {
    this.text = text
    isAllCaps = false
    textSize = 12f
    setTextColor(Color.WHITE)
    minHeight = dp(40f)
    minimumHeight = dp(40f)
    minWidth = 0
    minimumWidth = 0
    background = roundedBackground(
        color = if (danger) Color.rgb(91, 36, 45) else Color.rgb(35, 40, 50),
        radiusPx = dp(12f).toFloat(),
    )
    setOnClickListener(onClick)
}

fun View.setMargins(left: Int, top: Int, right: Int, bottom: Int) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.setMargins(left, top, right, bottom)
    layoutParams = params
}
