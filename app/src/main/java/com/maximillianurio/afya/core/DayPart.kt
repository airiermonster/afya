package com.maximillianurio.afya.core

import java.util.Calendar

/**
 * When in the day a weigh-in was taken. A body is lightest on waking and heavier by evening, so
 * comparing mornings with mornings is the fairest reading of a trend.
 */
enum class DayPart(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening");

    companion object {
        /** Morning runs 04:00–11:59, afternoon 12:00–16:59, and evening takes the rest of the night. */
        fun of(millis: Long): DayPart {
            val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 4..11 -> MORNING
                in 12..16 -> AFTERNOON
                else -> EVENING
            }
        }
    }
}
