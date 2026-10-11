package com.maximillianurio.afya.data

import android.content.Context
import android.graphics.Paint
import com.maximillianurio.afya.R
import com.maximillianurio.afya.core.Band
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.WeightUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max


/** One taped site's latest figure and how far it moved from the one before it. */
data class RecordCell(val centimetres: Double?, val changeCm: Double?)

/** A row of the tape table: one label, one cell for a single site or two for a limb. */
data class RecordSite(val label: String, val cells: List<RecordCell>, val recorded: String)

/**
 * Everything the printed record needs, gathered before a single page is drawn, so the renderer never
 * reaches for a database or a preference store and the whole sheet lays out in one pass.
 */
data class HealthRecord(
    val generatedMs: Long,
    val versionName: String,
    val name: String,
    val sexWord: String,
    val sex: Sex,
    val ageYears: Int,
    val birthLabel: String,
    val heightCm: Double,
    val unit: WeightUnit,
    val targetKg: Double,
    val activityLabel: String,
    val readings: List<ReadingsDb.Entry>,
    val rows: List<ReportRow>,
    val metrics: BodyMetrics?,
    val sites: List<RecordSite>,
)

/**
 * The arithmetic the sheet depends on, kept free of Android so it can be tested on the JVM: how many
 * pages a set of weigh-ins needs, and how a figure maps onto a chart or a band bar.
 */
object PdfPlan {
    /** Body rows per log sheet, fixed so the page count is known before the first stroke. */
    const val LOG_ROWS = 32

    fun logPages(readings: Int): Int = max(1, ceil(readings / LOG_ROWS.toDouble()).toInt())

    /** Summary, composition, tape and notes, then the log, then the method page. */
    fun pages(readings: Int): Int = 4 + logPages(readings)

    /** The value axis of a chart: every figure that must stay visible, with a little air. */
    fun domain(values: List<Double>, extra: List<Double> = emptyList()): Pair<Double, Double> {
        val all = (values + extra).filter { it.isFinite() }
        if (all.isEmpty()) return 0.0 to 1.0
        var low = all.first()
        var high = all.first()
        all.forEach { value ->
            if (value < low) low = value
            if (value > high) high = value
        }
        if (high - low < 0.5) {
            low -= 0.5
            high += 0.5
        }
        val pad = (high - low) * 0.08
        return low - pad to high + pad
    }

    fun xOf(moment: Long, from: Long, to: Long, left: Float, right: Float): Float {
        if (to <= from) return (left + right) / 2f
        return left + ((moment - from).toFloat() / (to - from)) * (right - left)
    }

    fun yOf(value: Double, low: Double, high: Double, top: Float, bottom: Float): Float {
        if (high <= low) return (top + bottom) / 2f
        val fraction = ((value - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
        return bottom - fraction * (bottom - top)
    }

    /** Where the healthy band sits along the same axis the metric's own bar is drawn on. */
    fun span(name: String, sex: Sex): Pair<Float, Float>? {
        val key = when (name) {
            "fat_mass" -> "fat"
            "water_mass" -> "water"
            "muscle_mass" -> "muscle"
            "skeletal_mass" -> "skeletal"
            "protein_mass" -> "protein"
            else -> name
        }
        val axis = BodyComposition.display(key, sex) ?: return null
        val band = BodyComposition.band(key, sex) ?: return null
        fun fractionOf(value: Double) =
            ((value - axis.first) / (axis.second - axis.first)).coerceIn(0.0, 1.0).toFloat()
        return fractionOf(band.first) to fractionOf(band.second)
    }
}

/**
 * The record a person carries to a clinic: who it is about, the trend, the full composition schedule
 * with the band each figure sits in, the tape measurements, every filed weigh-in, and a plain
 * statement of how each number was produced.
 *
 * Drawn with the platform's own PDF canvas, so no document library is added and nothing leaves the
 * phone. Paper is always the light scheme — a clinician reads black ink on white whatever the phone
 * was set to — and every derived figure is labelled an estimate.
 */
object RecordPdf {

    private const val PLOT_HEIGHT = 150f

    private val stamp = SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.getDefault())
    private val day = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val dayShort = SimpleDateFormat("d MMM", Locale.getDefault())

    fun build(context: Context, record: HealthRecord): ByteArray {
        val doc = Doc(context)
        val patient = record.name.ifBlank { doc.anon }
        val sheet = Sheet(
            context = context,
            runningHead = doc.runningHead,
            footerNote = "${doc.footer}  ·  ${doc.footerEstimates}",
            total = PdfPlan.pages(record.readings.size),
        )
        sheet.addPage()
        masthead(sheet, doc, record, patient)
        identity(sheet, doc, record, patient)
        glance(sheet, doc, record)
        trend(sheet, doc, record)
        movement(sheet, doc, record)
        composition(sheet, doc, record)
        tape(sheet, doc, record)
        notes(sheet, doc)
        log(sheet, doc, record)
        colophon(sheet, doc, record, patient)
        return sheet.finish()
    }

    // ---- page one: who, and what the scale says ----

    /**
     * The banner: the app icon's lit pine teal carrying the mark and the wordmark on the left, what the
     * document is and when it was made on the right. Whose record it is sits directly beneath, in ink,
     * because that is the line a reader checks first.
     */
    private fun masthead(sheet: Sheet, doc: Doc, record: HealthRecord, patient: String) {
        val top = Paper.MARGIN
        val band = BANNER_HEIGHT
        val onBand = Paper.white
        val onBandQuiet = Paper.alpha(Paper.white, 0.74f)
        sheet.brandBand(Paper.MARGIN, top, Paper.CONTENT, band, 12f)
        val markSide = 38f
        sheet.mark(Paper.MARGIN + 14f, top + (band - markSide) / 2f, markSide, onBand)
        sheet.text(
            doc.appName.lowercase(Locale.ROOT),
            Paper.MARGIN + 14f + markSide + 8f,
            top + 17f,
            27f,
            800,
            onBand,
            tracking = -0.03f,
        )
        sheet.text(
            doc.title.uppercase(Locale.ROOT),
            Paper.WIDTH - Paper.MARGIN - 18f,
            top + 22f,
            8.4f,
            700,
            onBand,
            Paint.Align.RIGHT,
            0.16f,
        )
        sheet.text(
            "${doc.generated} ${stamp.format(Date(record.generatedMs))}",
            Paper.WIDTH - Paper.MARGIN - 18f,
            top + 38f,
            8.4f,
            400,
            onBandQuiet,
            Paint.Align.RIGHT,
        )
        sheet.text(patient, Paper.MARGIN, top + band + 16f, 11.5f, 600, Paper.ink)
        sheet.text(
            "${record.sexWord}  ·  ${record.ageYears} ${doc.years}  ·  ${number(record.heightCm, 1)} ${doc.cm}",
            Paper.MARGIN, top + band + 34f, 9f, 400, Paper.inkMuted,
        )
        sheet.rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, top + band + 54f, 0.8f, Paper.hairline)
        sheet.y = top + band + 70f
    }

    /** The banner band's height in points. */
    private const val BANNER_HEIGHT = 64f

    private fun identity(sheet: Sheet, doc: Doc, record: HealthRecord, patient: String) {
        val newest = record.readings.firstOrNull()
        val oldest = record.readings.lastOrNull()
        val period = when {
            newest == null -> doc.none
            oldest == null || oldest.timestampMs == newest.timestampMs -> day.format(Date(newest.timestampMs))
            else -> "${day.format(Date(oldest.timestampMs))}  —  ${day.format(Date(newest.timestampMs))}"
        }
        val cells = listOf(
            doc.fieldName to patient,
            doc.fieldBirth to record.birthLabel.ifBlank { doc.none },
            doc.fieldAge to "${record.ageYears} ${doc.years}",
            doc.fieldHeight to "${number(record.heightCm, 1)} ${doc.cm}",
            doc.fieldWeighins to "${record.readings.size}",
            doc.fieldPeriod to period,
            doc.fieldUnit to record.unit.label,
            doc.fieldTarget to (
                record.targetKg.takeIf { it > 0 }
                    ?.let { "${number(it * 1000 / record.unit.gramsPer, 1)} ${record.unit.label}" } ?: doc.none
                ),
            doc.fieldActivity to record.activityLabel,
        )
        head(sheet, doc.headPatient)
        val pad = 14f
        val gap = 12f
        val colWidth = (Paper.CONTENT - 2 * pad - 2 * gap) / 3f
        val rowHeight = 30f
        val height = ceil(cells.size / 3.0).toFloat() * rowHeight + 2 * pad - 10f
        sheet.fill(Paper.MARGIN, sheet.y, Paper.CONTENT, height, Paper.tint, 8f)
        var top = sheet.y + pad
        cells.chunked(3).forEach { row ->
            row.forEachIndexed { index, (label, value) ->
                val x = Paper.MARGIN + pad + index * (colWidth + gap)
                sheet.text(label.uppercase(Locale.ROOT), x, top, 6.8f, 700, Paper.inkMuted, tracking = 0.12f)
                sheet.text(value, x, top + 10.5f, 9.6f, 600, Paper.ink)
            }
            top += rowHeight
        }
        sheet.y += height + 20f
    }

    private fun glance(sheet: Sheet, doc: Doc, record: HealthRecord) {
        head(sheet, doc.headGlance)
        val unit = record.unit
        val tiles = ArrayList<Tile>()
        record.readings.firstOrNull()?.let { newest ->
            val range = record.metrics?.healthyRangeKg
            tiles += Tile(
                label = doc.tileWeight,
                value = number(newest.kilograms * 1000 / unit.gramsPer, 1),
                unit = unit.label,
                band = when {
                    range == null -> Band.Unknown
                    newest.kilograms < range.first -> Band.Below
                    newest.kilograms > range.second -> Band.Above
                    else -> Band.Healthy
                },
                note = range?.let { "${doc.reference} ${number(it.first, 1)}–${number(it.second, 1)} ${unit.label}" }
                    ?: doc.noRange,
            )
        }
        listOf("bmi", "fat", "skeletal").mapNotNull { name -> record.rows.firstOrNull { it.name == name } }
            .forEach { row ->
                tiles += Tile(
                    label = when (row.name) {
                        "bmi" -> doc.tileBmi
                        "fat" -> doc.tileFat
                        else -> doc.tileSkeletal
                    },
                    value = row.valueText,
                    unit = row.unit,
                    band = row.band,
                    note = "${doc.reference} ${row.reference}",
                )
            }
        if (tiles.isEmpty()) return
        val tileGap = 11f
        val tileWidth = (Paper.CONTENT - tileGap * (tiles.size - 1)) / tiles.size
        val tileHeight = 76f
        tiles.forEachIndexed { index, tile ->
            val left = Paper.MARGIN + index * (tileWidth + tileGap)
            val tone = Paper.band(tile.band)
            sheet.fill(left, sheet.y, tileWidth, tileHeight, Paper.white, 8f)
            sheet.stroke(left, sheet.y, tileWidth, tileHeight, Paper.hairline, 8f, 0.8f)
            sheet.fill(left, sheet.y + 10f, 2.6f, tileHeight - 20f, tone, 1.3f)
            sheet.text(
                tile.label.uppercase(Locale.ROOT),
                left + 13f, sheet.y + 12f, 6.8f, 700, Paper.inkMuted, tracking = 0.12f,
            )
            sheet.text(tile.value, left + 13f, sheet.y + 25f, 21f, 800, tone, tracking = -0.02f)
            if (tile.unit.isNotEmpty()) {
                sheet.text(
                    tile.unit,
                    left + 17f + sheet.width(tile.value, 21f, 800, -0.02f),
                    sheet.y + 37f, 9f, 600, Paper.inkMuted,
                )
            }
            sheet.text(tile.bandWord, left + 13f, sheet.y + 52f, 7.6f, 700, tone, tracking = 0.08f)
            sheet.text(tile.note, left + 13f, sheet.y + 62f, 7.4f, 400, Paper.inkMuted)
        }
        sheet.y += tileHeight + 22f
    }

    private class Tile(
        val label: String,
        val value: String,
        val unit: String,
        val band: Band,
        val note: String,
    ) {
        val bandWord: String get() = band.word.uppercase(Locale.ROOT)
    }

    private fun trend(sheet: Sheet, doc: Doc, record: HealthRecord) {
        head(sheet, doc.headTrend)
        val unit = record.unit
        val points = record.readings.asReversed().map { it.timestampMs to it.grams / unit.gramsPer }
        val axisLeft = Paper.MARGIN + 38f
        val plotRight = Paper.MARGIN + Paper.CONTENT
        val top = sheet.y
        val bottom = top + PLOT_HEIGHT
        val corridor = record.metrics?.healthyRangeKg
            ?.let { it.first * 1000 / unit.gramsPer to it.second * 1000 / unit.gramsPer }
        val target = record.targetKg.takeIf { it > 0 }?.let { it * 1000 / unit.gramsPer }
        val (low, high) = PdfPlan.domain(
            points.map { it.second },
            listOfNotNull(corridor?.first, corridor?.second, target),
        )

        if (corridor != null) {
            val from = PdfPlan.yOf(corridor.second, low, high, top, bottom)
            val to = PdfPlan.yOf(corridor.first, low, high, top, bottom)
            sheet.fill(axisLeft, from, plotRight - axisLeft, to - from, Paper.alpha(Paper.brandWash, 0.92f))
            sheet.text(doc.healthyBand, axisLeft + 7f, from + 4f, 6.8f, 700, Paper.brandDeep, tracking = 0.1f)
        }
        for (grid in 0..4) {
            val value = low + (high - low) * grid / 4
            val y = PdfPlan.yOf(value, low, high, top, bottom)
            sheet.rule(axisLeft, plotRight, y, 0.6f, Paper.hairline)
            sheet.text(number(value, 1), axisLeft - 8f, y - 4.2f, 7.4f, 400, Paper.inkMuted, Paint.Align.RIGHT)
        }
        if (target != null) {
            val y = PdfPlan.yOf(target, low, high, top, bottom)
            sheet.dashed(axisLeft, plotRight, y, Paper.brandDeep)
            sheet.text(
                "${doc.target} ${number(target, 1)} ${unit.label}",
                plotRight - 5f, y - 13f, 7.2f, 700, Paper.brandDeep, Paint.Align.RIGHT,
            )
        }
        val from = points.firstOrNull()?.first ?: 0L
        val to = points.lastOrNull()?.first ?: 1L
        val plotted = points.map {
            PdfPlan.xOf(it.first, from, to, axisLeft, plotRight) to PdfPlan.yOf(it.second, low, high, top, bottom)
        }
        if (plotted.size > 1) {
            sheet.curve(plotted, Paper.brand, 1.7f, bottom, Paper.alpha(Paper.brand, 0.12f))
        }
        plotted.forEach { (x, y) -> sheet.dot(x, y, 1.9f, Paper.brand) }
        plotted.lastOrNull()?.let { (x, y) -> sheet.dot(x, y, 3.2f, Paper.brand, Paper.white) }

        if (points.isNotEmpty()) {
            sheet.text(dayShort.format(Date(from)), axisLeft, bottom + 9f, 7.4f, 400, Paper.inkMuted)
            sheet.text(
                "${doc.weighins} ${points.size}",
                axisLeft + (plotRight - axisLeft) / 2f, bottom + 9f, 7.4f, 400, Paper.inkMuted, Paint.Align.CENTER,
            )
            sheet.text(dayShort.format(Date(to)), plotRight, bottom + 9f, 7.4f, 400, Paper.inkMuted, Paint.Align.RIGHT)
        }
        sheet.y = bottom + 28f
    }

    private fun movement(sheet: Sheet, doc: Doc, record: HealthRecord) {
        val readings = record.readings
        if (readings.size < 2) {
            sheet.text(doc.needMore, Paper.MARGIN, sheet.y, 9f, 400, Paper.inkMuted)
            sheet.y += 16f
            return
        }
        val unit = record.unit
        val first = readings.last()
        val last = readings.first()
        val changeDisplay = (last.kilograms - first.kilograms) * 1000 / unit.gramsPer
        val days = (last.timestampMs - first.timestampMs) / 86_400_000.0
        val lines = mutableListOf(
            "${doc.moved} ${signed(changeDisplay, 1)} ${unit.label}   ·   " +
                "${doc.from} ${number(first.grams / unit.gramsPer, 1)} ${unit.label} ${day.format(Date(first.timestampMs))}   ·   " +
                "${doc.to} ${number(last.grams / unit.gramsPer, 1)} ${unit.label} ${day.format(Date(last.timestampMs))}",
        )
        // A weekly rate off two same-day weigh-ins is a fiction, so the sheet waits a week for one.
        if (days >= 7) {
            lines += "${doc.rate} ${signed(changeDisplay / (days / 7.0), 1)} ${unit.label} ${doc.perWeek}"
        }
        if (record.targetKg > 0) {
            val remaining = (record.targetKg - last.kilograms) * 1000 / unit.gramsPer
            lines += if (abs(remaining) < 0.05) doc.atTarget
            else "${doc.remaining} ${number(abs(remaining), 1)} ${unit.label} ${doc.toTarget}"
        }
        lines.forEach { line ->
            sheet.needs(16f)
            sheet.text(line, Paper.MARGIN, sheet.y, 9f, 400, Paper.ink)
            sheet.y += 14f
        }
        sheet.y += 6f
    }

    // ---- page two: the whole schedule ----

    private fun composition(sheet: Sheet, doc: Doc, record: HealthRecord) {
        sheet.addPage()
        head(sheet, doc.headComposition)
        lead(sheet, doc.compositionLead)
        val columns = listOf(
            Column(128f, doc.colMetric, Paint.Align.LEFT),
            Column(56f, doc.colValue, Paint.Align.RIGHT),
            Column(62f, doc.colBand, Paint.Align.LEFT),
            Column(80f, doc.colReference, Paint.Align.RIGHT),
            Column(60f, doc.colChange, Paint.Align.RIGHT),
            Column(121f, doc.colWhere, Paint.Align.LEFT),
        )
        tableHead(sheet, columns)
        record.rows.forEach { row ->
            sheet.needs(22f)
            tableRow(sheet, columns, row, record.sex)
        }
        sheet.y += 8f
        sheet.rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, sheet.y, 0.8f, Paper.hairline)
        sheet.y += 9f
        doc.compositionFoot.forEach { line ->
            sheet.needs(13f)
            sheet.text(line, Paper.MARGIN, sheet.y, 8f, 400, Paper.inkMuted)
            sheet.y += 12f
        }
    }

    private class Column(val width: Float, val label: String, val align: Paint.Align)

    private fun tableHead(sheet: Sheet, columns: List<Column>) {
        sheet.needs(24f)
        sheet.fill(Paper.MARGIN, sheet.y - 4f, Paper.CONTENT, 17f, Paper.tint)
        var x = Paper.MARGIN + 6f
        columns.forEach { column ->
            sheet.text(
                column.label.uppercase(Locale.ROOT),
                if (column.align == Paint.Align.RIGHT) x + column.width - 12f else x,
                sheet.y, 6.8f, 700, Paper.inkMuted, column.align, 0.1f,
            )
            x += column.width
        }
        sheet.y += 20f
    }

    private fun tableRow(sheet: Sheet, columns: List<Column>, row: ReportRow, sex: Sex) {
        val top = sheet.y
        val tone = Paper.band(row.band)
        var x = Paper.MARGIN + 6f
        columns.forEachIndexed { index, column ->
            val right = x + column.width - 12f
            when (index) {
                0 -> sheet.text(row.title, x, top, 8.8f, 600, Paper.ink)
                1 -> sheet.text(row.valueText, right, top, 10f, 800, tone, Paint.Align.RIGHT, -0.01f)
                2 -> if (row.band != Band.Unknown) {
                    sheet.text(row.bandWord, x, top + 1f, 7.6f, 700, tone)
                }
                3 -> sheet.text(row.reference, right, top + 1f, 8f, 400, Paper.inkMuted, Paint.Align.RIGHT)
                4 -> sheet.text(
                    if (row.hasChange) row.changeText else "—",
                    right, top + 1f, 8f, 400, if (row.hasChange) Paper.ink else Paper.inkFaint, Paint.Align.RIGHT,
                )
                else -> PdfPlan.span(row.name, sex)?.let { span ->
                    bandBar(sheet, x, top + 6f, column.width - 18f, row.position, span, tone)
                }
            }
            x += column.width
        }
        sheet.rule(Paper.MARGIN + 6f, Paper.MARGIN + Paper.CONTENT - 6f, top + 17f, 0.6f, Paper.hairline)
        sheet.y = top + 21f
    }

    /** The band bar from the interface, in print: the track, the healthy span, the marker. */
    private fun bandBar(
        sheet: Sheet,
        left: Float,
        centre: Float,
        width: Float,
        position: Float,
        span: Pair<Float, Float>,
        tone: Int,
    ) {
        sheet.fill(left, centre - 1.6f, width, 3.2f, Paper.tint, 1.6f)
        val from = left + width * span.first
        sheet.fill(from, centre - 1.6f, width * (span.second - span.first), 3.2f, Paper.alpha(tone, 0.34f), 1.6f)
        sheet.dot(left + width * position.coerceIn(0f, 1f), centre, 3f, tone, Paper.white)
    }

    // ---- page three: the tape, and room to write ----

    private fun tape(sheet: Sheet, doc: Doc, record: HealthRecord) {
        sheet.addPage()
        head(sheet, doc.headTape)
        lead(sheet, doc.tapeLead)
        val columns = listOf(
            Column(150f, doc.colSite, Paint.Align.LEFT),
            Column(84f, doc.colLeft, Paint.Align.RIGHT),
            Column(84f, doc.colRight, Paint.Align.RIGHT),
            Column(88f, doc.colChange, Paint.Align.RIGHT),
            Column(101f, doc.colRecorded, Paint.Align.RIGHT),
        )
        tableHead(sheet, columns)
        if (record.sites.isEmpty()) {
            sheet.text(doc.tapeNone, Paper.MARGIN + 6f, sheet.y, 9f, 400, Paper.inkMuted)
            sheet.y += 21f
        }
        record.sites.forEach { site ->
            sheet.needs(22f)
            val top = sheet.y
            sheet.text(site.label, Paper.MARGIN + 6f, top, 8.8f, 600, Paper.ink)
            var x = Paper.MARGIN + 6f + columns[0].width
            if (site.cells.size == 1) {
                val cell = site.cells.first()
                sheet.text(
                    cell.centimetres?.let { "${number(it, 1)} ${doc.cm}" } ?: doc.none,
                    x + columns[1].width + columns[2].width - 12f, top, 8.8f, 400, Paper.ink, Paint.Align.RIGHT,
                )
                x += columns[1].width + columns[2].width
                sheet.text(
                    cell.changeCm?.let { "${signed(it, 1)} ${doc.cm}" } ?: "—",
                    x + columns[3].width - 12f, top, 8.4f, 400, Paper.inkMuted, Paint.Align.RIGHT,
                )
            } else {
                site.cells.forEachIndexed { index, cell ->
                    sheet.text(
                        cell.centimetres?.let { number(it, 1) } ?: "—",
                        x + columns[1 + index].width - 12f, top, 8.8f, 400, Paper.ink, Paint.Align.RIGHT,
                    )
                }
                x += columns[1].width + columns[2].width
                val change = site.cells.mapIndexed { index, cell ->
                    val side = if (index == 0) doc.sideLeft else doc.sideRight
                    "$side ${cell.changeCm?.let { signed(it, 1) } ?: "—"}"
                }.joinToString("  ·  ")
                sheet.text(change, x + columns[3].width - 12f, top, 8.4f, 400, Paper.inkMuted, Paint.Align.RIGHT)
            }
            x += columns[3].width
            sheet.text(site.recorded, x + columns[4].width - 12f, top, 8.4f, 400, Paper.inkMuted, Paint.Align.RIGHT)
            sheet.rule(Paper.MARGIN + 6f, Paper.MARGIN + Paper.CONTENT - 6f, top + 17f, 0.6f, Paper.hairline)
            sheet.y = top + 21f
        }
    }

    private fun notes(sheet: Sheet, doc: Doc) {
        sheet.needs(170f)
        sheet.y += 10f
        head(sheet, doc.headNotes)
        var top = sheet.y + 4f
        repeat(7) {
            sheet.rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, top, 0.7f, Paper.hairline)
            top += 22f
        }
        sheet.y = top + 4f
    }

    // ---- the log ----

    private fun log(sheet: Sheet, doc: Doc, record: HealthRecord) {
        sheet.addPage()
        head(sheet, doc.headLog)
        lead(sheet, doc.logLead)
        val unit = record.unit
        // The device's own figure leads the row. A separate kilogram column only earns its place when
        // the display unit is something else; on a kg sheet it would print the same number twice.
        val inKg = unit == WeightUnit.KG
        val columns = if (inKg) listOf(
            Column(88f, doc.colDate, Paint.Align.LEFT),
            Column(40f, doc.colTime, Paint.Align.RIGHT),
            Column(70f, "${doc.colWeight} ${unit.label}", Paint.Align.RIGHT),
            Column(52f, doc.colBmi, Paint.Align.RIGHT),
            Column(58f, doc.colFat, Paint.Align.RIGHT),
            Column(64f, doc.colSkeletal, Paint.Align.RIGHT),
            Column(60f, doc.colWater, Paint.Align.RIGHT),
            Column(75f, doc.colImpedance, Paint.Align.RIGHT),
        ) else listOf(
            Column(88f, doc.colDate, Paint.Align.LEFT),
            Column(40f, doc.colTime, Paint.Align.RIGHT),
            Column(62f, "${doc.colWeight} ${unit.label}", Paint.Align.RIGHT),
            Column(46f, doc.colKg, Paint.Align.RIGHT),
            Column(42f, doc.colBmi, Paint.Align.RIGHT),
            Column(48f, doc.colFat, Paint.Align.RIGHT),
            Column(54f, doc.colSkeletal, Paint.Align.RIGHT),
            Column(50f, doc.colWater, Paint.Align.RIGHT),
            Column(77f, doc.colImpedance, Paint.Align.RIGHT),
        )
        record.readings.chunked(PdfPlan.LOG_ROWS).forEachIndexed { index, chunk ->
            if (index > 0) sheet.addPage()
            tableHead(sheet, columns)
            chunk.forEach { entry ->
                val top = sheet.y
                sheet.text(day.format(Date(entry.timestampMs)), Paper.MARGIN + 6f, top, 8.4f, 600, Paper.ink)
                var x = Paper.MARGIN + 6f + columns[0].width
                sheet.text(
                    DateFmt.clock(entry.timestampMs), x + columns[1].width - 12f, top,
                    8.4f, 400, Paper.inkMuted, Paint.Align.RIGHT,
                )
                x += columns[1].width
                var slot = 2
                fun cell(value: String, size: Float, weight: Int, color: Int, tracking: Float = 0f) {
                    sheet.text(value, x + columns[slot].width - 12f, top, size, weight, color, Paint.Align.RIGHT, tracking)
                    x += columns[slot].width
                    slot += 1
                }
                cell(number(entry.grams / unit.gramsPer, 1), 9.2f, 700, Paper.ink, -0.01f)
                if (!inKg) cell(number(entry.kilograms, 1), 8.4f, 400, Paper.inkMuted)
                cell(entry.bmi?.let { number(it, 1) } ?: "—", 8.4f, 400, Paper.ink)
                cell(entry.fatPercent?.let { number(it, 1) } ?: "—", 8.4f, 400, Paper.ink)
                cell(entry.skeletalPercent?.let { number(it, 1) } ?: "—", 8.4f, 400, Paper.ink)
                cell(entry.waterPercent?.let { number(it, 1) } ?: "—", 8.4f, 400, Paper.ink)
                cell(entry.impedanceOhms?.let { number(it, 0) } ?: "—", 8.4f, 400, Paper.ink)
                sheet.rule(Paper.MARGIN + 6f, Paper.MARGIN + Paper.CONTENT - 6f, top + 15f, 0.6f, Paper.hairline)
                sheet.y = top + 18f
            }
        }
    }

    // ---- the last page: how the numbers were made ----

    private fun colophon(sheet: Sheet, doc: Doc, record: HealthRecord, patient: String) {
        sheet.addPage()
        head(sheet, doc.headMethod)
        doc.method.forEach { paragraph ->
            sheet.wrap(paragraph, 8.8f, 400, Paper.CONTENT).forEach { line ->
                sheet.needs(13f)
                sheet.text(line, Paper.MARGIN, sheet.y, 8.8f, 400, Paper.ink)
                sheet.y += 12.5f
            }
            sheet.y += 9f
        }
        sheet.y += 6f
        sheet.rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, sheet.y, 0.8f, Paper.hairline)
        sheet.y += 18f
        sheet.text(doc.signedBy, Paper.MARGIN, sheet.y, 11.5f, 700, Paper.ink)
        sheet.y += 18f
        sheet.text(doc.signedFrom, Paper.MARGIN, sheet.y, 9f, 400, Paper.inkMuted)
        sheet.y += 22f
        val sites = record.sites.count { site -> site.cells.any { it.centimetres != null } }
        sheet.text(
            "${doc.colophonPatient} ${patient.ifBlank { doc.anon }}   ·   ${doc.weighins} ${record.readings.size}   ·   " +
                "${doc.colophonSites} $sites   ·   ${doc.generated} ${stamp.format(Date(record.generatedMs))}   ·   " +
                "${doc.appName} v${record.versionName}",
            Paper.MARGIN, sheet.y, 7.6f, 400, Paper.inkMuted,
        )
        sheet.y += 14f
    }

    // ---- shared marks ----

    private fun head(sheet: Sheet, title: String) {
        sheet.needs(36f)
        sheet.text(title.uppercase(Locale.ROOT), Paper.MARGIN, sheet.y, 8.4f, 700, Paper.brandDeep, tracking = 0.16f)
        sheet.y += 13f
        sheet.fill(Paper.MARGIN, sheet.y, 22f, 2f, Paper.brand)
        sheet.y += 10f
    }

    private fun number(value: Double, places: Int): String = "%.${places}f".format(Locale.ROOT, value)

    /** The sentence under a section head, wrapped to the measure rather than run off the page. */
    private fun lead(sheet: Sheet, value: String) {
        sheet.wrap(value, 8.6f, 400, Paper.CONTENT).forEach { line ->
            sheet.text(line, Paper.MARGIN, sheet.y, 8.6f, 400, Paper.inkMuted)
            sheet.y += 11.5f
        }
        sheet.y += 7.5f
    }

    private fun signed(value: Double, places: Int): String =
        (if (value >= 0) "+" else "−") + number(abs(value), places)

    /** The document's own words, read from the same resource file every other screen uses. */
    private class Doc(private val context: Context) {
        private fun at(id: Int): String = context.getString(id)

        val title = at(R.string.pdf_title)
        val appName = at(R.string.app_name)
        val runningHead = at(R.string.pdf_running_head)
        val footer = at(R.string.pdf_footer)
        val footerEstimates = at(R.string.pdf_footer_estimates)
        val generated = at(R.string.pdf_generated)
        val anon = at(R.string.pdf_anon)
        val none = at(R.string.pdf_none)
        val years = at(R.string.pdf_years)
        val cm = at(R.string.pdf_cm)
        val reference = at(R.string.pdf_reference)
        val noRange = at(R.string.pdf_no_range)
        val weighins = at(R.string.pdf_weighins)

        val headPatient = at(R.string.pdf_head_patient)
        val headGlance = at(R.string.pdf_head_glance)
        val headTrend = at(R.string.pdf_head_trend)
        val headComposition = at(R.string.pdf_head_composition)
        val headTape = at(R.string.pdf_head_tape)
        val headNotes = at(R.string.pdf_head_notes)
        val headLog = at(R.string.pdf_head_log)
        val headMethod = at(R.string.pdf_head_method)

        val fieldName = at(R.string.pdf_field_name)
        val fieldBirth = at(R.string.pdf_field_birth)
        val fieldAge = at(R.string.pdf_field_age)
        val fieldHeight = at(R.string.pdf_field_height)
        val fieldWeighins = at(R.string.pdf_field_weighins)
        val fieldPeriod = at(R.string.pdf_field_period)
        val fieldUnit = at(R.string.pdf_field_unit)
        val fieldTarget = at(R.string.pdf_field_target)
        val fieldActivity = at(R.string.pdf_field_activity)

        val tileWeight = at(R.string.pdf_tile_weight)
        val tileBmi = at(R.string.pdf_tile_bmi)
        val tileFat = at(R.string.pdf_tile_fat)
        val tileSkeletal = at(R.string.pdf_tile_skeletal)

        val healthyBand = at(R.string.pdf_healthy_band)
        val target = at(R.string.pdf_target)
        val moved = at(R.string.pdf_moved)
        val from = at(R.string.pdf_from)
        val to = at(R.string.pdf_to)
        val rate = at(R.string.pdf_rate)
        val perWeek = at(R.string.pdf_per_week)
        val remaining = at(R.string.pdf_remaining)
        val toTarget = at(R.string.pdf_to_target)
        val atTarget = at(R.string.pdf_at_target)
        val needMore = at(R.string.pdf_need_more)

        val compositionLead = at(R.string.pdf_composition_lead)
        val tapeLead = at(R.string.pdf_tape_lead)
        val logLead = at(R.string.pdf_log_lead)
        val tapeNone = at(R.string.pdf_tape_none)
        val compositionFoot = listOf(at(R.string.pdf_foot_1), at(R.string.pdf_foot_2))
        val method = listOf(
            at(R.string.pdf_method_1),
            at(R.string.pdf_method_2),
            at(R.string.pdf_method_3),
            at(R.string.pdf_method_4),
        )

        val colMetric = at(R.string.pdf_col_metric)
        val colValue = at(R.string.pdf_col_value)
        val colBand = at(R.string.pdf_col_band)
        val colReference = at(R.string.pdf_col_reference)
        val colChange = at(R.string.pdf_col_change)
        val colWhere = at(R.string.pdf_col_where)
        val colSite = at(R.string.pdf_col_site)
        val colLeft = at(R.string.pdf_col_left)
        val colRight = at(R.string.pdf_col_right)
        val colRecorded = at(R.string.pdf_col_recorded)
        val colDate = at(R.string.pdf_col_date)
        val colTime = at(R.string.pdf_col_time)
        val colWeight = at(R.string.pdf_col_weight)
        val colKg = at(R.string.pdf_col_kg)
        val colBmi = at(R.string.pdf_col_bmi)
        val colFat = at(R.string.pdf_col_fat)
        val colSkeletal = at(R.string.pdf_col_skeletal)
        val colWater = at(R.string.pdf_col_water)
        val colImpedance = at(R.string.pdf_col_impedance)
        val sideLeft = at(R.string.pdf_side_left)
        val sideRight = at(R.string.pdf_side_right)

        val signedBy = at(R.string.pdf_signed_by)
        val signedFrom = at(R.string.pdf_signed_from)
        val colophonPatient = at(R.string.pdf_colophon_patient)
        val colophonSites = at(R.string.pdf_colophon_sites)
    }
}
