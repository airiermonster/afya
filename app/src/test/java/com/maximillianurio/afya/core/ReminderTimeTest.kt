package com.maximillianurio.afya.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")
    private val london = ZoneId.of("Europe/London")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun laterTodayFiresToday() {
        val now = at(nairobi, 2026, 10, 10, 5, 0)
        assertEquals(at(nairobi, 2026, 10, 10, 7, 0), ReminderTime.next(now, 7 * 60, nairobi))
    }

    @Test
    fun alreadyPastFiresTomorrow() {
        val now = at(nairobi, 2026, 10, 10, 23, 18)
        assertEquals(at(nairobi, 2026, 10, 11, 7, 0), ReminderTime.next(now, 7 * 60, nairobi))
    }

    @Test
    fun theSameMinuteDoesNotFireTwice() {
        val now = at(nairobi, 2026, 10, 10, 7, 0)
        assertEquals(at(nairobi, 2026, 10, 11, 7, 0), ReminderTime.next(now, 7 * 60, nairobi))
    }

    @Test
    fun aClockChangeKeepsTheWallClockTime() {
        // UK clocks go back on 25 October 2026; 07:30 stays 07:30 local, 25 hours after the day before.
        val now = at(london, 2026, 10, 24, 8, 0)
        assertEquals(at(london, 2026, 10, 25, 7, 30), ReminderTime.next(now, 7 * 60 + 30, london))
    }

    @Test
    fun labelsAreTwentyFourHour() {
        assertEquals("07:05", ReminderTime.label(7 * 60 + 5))
        assertEquals("21:30", ReminderTime.label(21 * 60 + 30))
    }
}
