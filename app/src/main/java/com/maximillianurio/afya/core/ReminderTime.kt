package com.maximillianurio.afya.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When the next weigh-in reminder is due, worked out in the phone's own time zone. */
object ReminderTime {

    /**
     * The next moment after [nowMs] that the clock reads [minutes] past midnight, today if that is
     * still at least a minute away and tomorrow otherwise. Built from the local date rather than by
     * adding 24 hours, so a daylight-saving change never moves the reminder by an hour.
     */
    fun next(nowMs: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        require(minutes in 0 until 24 * 60) { "minutes must be within one day: $minutes" }
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val at = LocalTime.of(minutes / 60, minutes % 60)
        var candidate = now.with(at).withSecond(0).withNano(0)
        if (!candidate.isAfter(now.plusMinutes(1))) candidate = candidate.plusDays(1).with(at)
        return candidate.toInstant().toEpochMilli()
    }

    /** "07:30" for 450, in the 24-hour form the picker and the setting both show. */
    fun label(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
