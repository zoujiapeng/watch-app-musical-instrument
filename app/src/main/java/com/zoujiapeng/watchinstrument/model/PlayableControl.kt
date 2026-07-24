package com.zoujiapeng.watchinstrument.model

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left <= right)
        require(top <= bottom)
    }

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

data class PlayableControl(
    val id: Int,
    val region: NormalizedRect,
    val label: String,
    val midiNote: Int? = null,
    val percussion: Percussion? = null,
    val zIndex: Int = 0,
)
