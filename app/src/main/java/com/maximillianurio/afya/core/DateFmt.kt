package com.maximillianurio.afya.core

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFmt {
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val day = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val serial = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val dayPrint = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthTab = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    private val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val birth = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

    fun clock(millis: Long): String = clock.format(Date(millis))
    fun full(millis: Long): String = full.format(Date(millis))

    /** The month a ledger tab prints, and the key the months are grouped by. */
    fun monthTab(millis: Long): String = monthTab.format(Date(millis))
    fun monthKey(millis: Long): String = monthKey.format(Date(millis))

    /** The date as a form writes it: day, month, year, then the time to the minute. */
    fun serial(millis: Long): String = serial.format(Date(millis))

    /** The date a tape measurement was taken, as the printed record shows it. */
    fun printDay(millis: Long): String = dayPrint.format(Date(millis))

    /** The heading of a month's page in the ledger. */
    fun month(millis: Long): String = month.format(Date(millis))

    /** The birthday the profile form stores, as [java.time.LocalDate.toEpochDay]. */
    fun birthDate(epochDay: Long): String =
        if (epochDay <= 0L) "" else LocalDate.ofEpochDay(epochDay).format(birth)

    /** Half-open window covering the whole calendar day [millis] falls in. */
    fun dayBounds(millis: Long): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    fun dayLabel(millis: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val days = when {
            target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> 0

            target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> 1

            else -> -1
        }
        return when (days) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> day.format(Date(millis))
        }
    }
}
