package com.maximillianurio.afya.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.core.Band
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.DayPart
import com.maximillianurio.afya.core.Report
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.Trend
import com.maximillianurio.afya.core.TrendWeight
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.WeightUnit
import com.maximillianurio.afya.core.deltaParts
import com.maximillianurio.afya.data.ReadingsDb
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.AfyaDialog
import com.maximillianurio.afya.ui.components.ChoicePill
import com.maximillianurio.afya.ui.components.DeltaPill
import com.maximillianurio.afya.ui.components.EmptyState
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.ListRow
import com.maximillianurio.afya.ui.components.Scene
import com.maximillianurio.afya.ui.components.SectionHead
import com.maximillianurio.afya.ui.components.StatusPill
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.components.TrendArea
import com.maximillianurio.afya.ui.components.ValueRow
import com.maximillianurio.afya.ui.components.enterLift
import com.maximillianurio.afya.ui.components.metricIcon
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.MonoBody
import com.maximillianurio.afya.ui.theme.TileValue
import com.maximillianurio.afya.ui.theme.afya

/**
 * Everything filed, in the order it is looked for: the weight line with its trend, the latest
 * composition right under it, then the ledger and the tape. One pair of filters (month, time of day)
 * narrows the chart and the ledger together, and two weigh-ins can be set side by side.
 */
@Composable
fun RecordsScreen(
    state: AppState,
    allReadings: () -> List<ReadingsDb.Entry>,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
    onSelectMetric: (ReportRow) -> Unit,
    measurements: () -> Map<String, ReadingsDb.Measurement>,
    onSaveMeasurement: (String, Double) -> Boolean,
    onDeleteMeasurement: (Long) -> Unit,
    onDeleteReading: (Long) -> Unit,
    onLengthUnits: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    val unit = profile.unit

    val all = remember(allReadings, state.dataVersion) { allReadings() }
    val months = remember(all) { groupMonths(all) }
    val numbers = remember(all) {
        all.asReversed().mapIndexed { index, entry -> entry.id to index + 1 }.toMap()
    }
    var month by rememberSaveable(state.dataVersion) { mutableIntStateOf(0) }
    var part by rememberSaveable { mutableStateOf<DayPart?>(null) }

    val selected = remember(all, month, part) {
        val inMonth = when {
            months.isEmpty() -> emptyList()
            month == 0 || month > months.size -> all
            else -> months[month - 1].entries
        }
        if (part == null) inMonth else inMonth.filter { DayPart.of(it.timestampMs) == part }
    }
    val ascending = remember(selected) { selected.asReversed().map { it.timestampMs to it.grams / unit.gramsPer } }
    val trend = remember(ascending) {
        if (TrendWeight.meaningful(ascending)) TrendWeight.series(ascending) else null
    }

    val rows: List<ReportRow> = remember(all.firstOrNull(), unit, state.dataVersion) {
        val latest = all.firstOrNull() ?: return@remember emptyList()
        val metrics = metricsOf(latest) ?: return@remember emptyList()
        val olderEntry = all.getOrNull(1)
        val older = olderEntry?.let { Report.comparable(metrics, latest.kilograms, metricsOf(it), it.kilograms) }
        if (!profile.complete) {
            emptyList()
        } else {
            Report.rows(metrics, latest.kilograms, profile.ageYears, profile.sex, unit, older)
        }
    }
    // The weigh-in before was read through a different height, age or sex, so no change is printed.
    val rebased = remember(all.firstOrNull(), all.getOrNull(1), state.dataVersion) {
        val latest = all.firstOrNull()
        val before = all.getOrNull(1)
        if (latest == null || before == null) return@remember false
        val now = metricsOf(latest)
        val then = metricsOf(before)
        now != null && then != null && !Report.sameBasis(now, latest.kilograms, then, before.kilograms)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
    ) {
        SectionHead(
            title = stringResource(R.string.tab_history),
            action = {
                Text(
                    text = stringResource(R.string.records_count, all.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.inkMuted,
                )
            },
        )

        if (all.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            AfyaCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    title = stringResource(R.string.records_empty_title),
                    body = stringResource(R.string.records_empty_body),
                    scene = Scene.Records,
                )
            }
            return@Column
        }

        // One set of filters, read by the chart and the ledger alike.
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                label = stringResource(R.string.ledger_month_all),
                selected = month == 0,
                onClick = { month = 0 },
            )
            months.forEachIndexed { index, entry ->
                ChoicePill(
                    label = entry.tab,
                    selected = month == index + 1,
                    onClick = { month = index + 1 },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                label = stringResource(R.string.records_all_day),
                selected = part == null,
                onClick = { part = null },
            )
            DayPart.entries.forEach { option ->
                ChoicePill(
                    label = option.label,
                    selected = part == option,
                    onClick = { part = option },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Column(modifier = Modifier.fillMaxWidth().enterLift(0)) {
            SectionHead(title = stringResource(R.string.records_trend_heading))
            Spacer(Modifier.height(12.dp))
            AfyaCard(modifier = Modifier.fillMaxWidth()) {
                TrendArea(
                    points = ascending,
                    trend = trend,
                    hue = afya.hue("weight"),
                    goal = profile.targetKg.takeIf { it > 0 }?.let { it * 1000 / unit.gramsPer },
                    startLabel = selected.lastOrNull()?.let { DateFmt.dayLabel(it.timestampMs) },
                    endLabel = selected.firstOrNull()?.let { DateFmt.dayLabel(it.timestampMs) },
                    emptyLabel = stringResource(
                        if (selected.isEmpty()) R.string.records_filter_empty else R.string.ledger_low_data
                    ),
                    description = ascending.takeIf { it.size >= 2 }?.let {
                        stringResource(
                            R.string.records_chart_description,
                            it.size,
                            Units.format(selected.last().grams, unit),
                            Units.format(selected.first().grams, unit),
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(4.dp))
                ValueRow(
                    label = stringResource(R.string.ledger_stat_current),
                    value = selected.firstOrNull()?.let { Units.format(it.grams, unit) } ?: "--",
                )
                trend?.lastOrNull()?.let { (_, value) ->
                    ValueRow(
                        label = stringResource(R.string.ledger_stat_trend),
                        value = Units.format(value * unit.gramsPer, unit),
                    )
                }
                ValueRow(label = stringResource(R.string.ledger_stat_change), value = changeText(selected, unit))
                ValueRow(label = stringResource(R.string.ledger_stat_average), value = averageText(selected, unit))
                if (profile.targetKg > 0 && selected.isNotEmpty()) {
                    val toGo = selected.first().kilograms - profile.targetKg
                    ValueRow(
                        label = stringResource(R.string.ledger_stat_goal),
                        value = if (kotlin.math.abs(toGo) < 0.05) {
                            stringResource(R.string.ledger_goal_reached)
                        } else {
                            Units.format(kotlin.math.abs(toGo) * 1000.0, unit)
                        },
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth().enterLift(1)) {
                SectionHead(title = stringResource(R.string.schedule_heading))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.schedule_of,
                        DateFmt.dayLabel(all.first().timestampMs),
                        DateFmt.clock(all.first().timestampMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
                if (rebased) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.schedule_rebased),
                        style = MaterialTheme.typography.bodySmall,
                        color = afya.inkMuted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                CompositionList(rows, onSelectMetric)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.estimates_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Ledger(
            title = if (month == 0 || month > months.size) {
                stringResource(R.string.records_ledger_heading)
            } else {
                months[month - 1].title
            },
            entries = selected,
            numbers = numbers,
            unit = unit,
            diagnostics = state.settings.diagnostics,
            metricsOf = metricsOf,
            onDeleteReading = onDeleteReading,
        )

        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHead(title = stringResource(R.string.measures_heading))
            Spacer(Modifier.height(12.dp))
            MeasuresSection(
                state = state,
                measurements = measurements,
                onSave = onSaveMeasurement,
                onDelete = onDeleteMeasurement,
                onUnits = onLengthUnits,
            )
        }
    }
}

/**
 * The filed weigh-ins, newest first, a page at a time so a year of mornings never renders at once.
 * A row opens its own record in place; in compare mode a row is picked instead, and two picks set
 * the weigh-ins side by side above the list.
 */
@Composable
private fun Ledger(
    title: String,
    entries: List<ReadingsDb.Entry>,
    numbers: Map<Long, Int>,
    unit: WeightUnit,
    diagnostics: Boolean,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
    onDeleteReading: (Long) -> Unit,
) {
    val afya = MaterialTheme.afya
    var open by rememberSaveable(entries.size) { mutableStateOf(-1L) }
    var pendingDelete by remember { mutableStateOf<ReadingsDb.Entry?>(null) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var picks by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var shown by rememberSaveable(entries.size) { mutableIntStateOf(PAGE) }

    SectionHead(
        title = title,
        action = {
            if (entries.size >= 2) {
                TextAction(
                    label = stringResource(if (comparing) R.string.records_compare_done else R.string.records_compare),
                    onClick = {
                        comparing = !comparing
                        picks = emptyList()
                        open = -1L
                    },
                )
            }
        },
    )
    if (comparing) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.records_compare_hint),
            style = MaterialTheme.typography.bodySmall,
            color = afya.inkMuted,
        )
        val pair = picks.mapNotNull { id -> entries.firstOrNull { it.id == id } }.sortedBy { it.timestampMs }
        if (pair.size == 2) {
            Spacer(Modifier.height(12.dp))
            CompareCard(pair[0], pair[1], unit, metricsOf)
        }
    }
    Spacer(Modifier.height(12.dp))

    if (entries.isEmpty()) {
        AfyaCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.records_filter_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = afya.inkMuted,
            )
        }
        return
    }

    AfyaCard(contentPadding = 10.dp, modifier = Modifier.fillMaxWidth()) {
        val page = entries.take(shown)
        page.forEachIndexed { index, entry ->
            val older = entries.getOrNull(index + 1)
            val (trend, delta) = older?.let {
                deltaParts(entry.kilograms - it.kilograms, unit)
            } ?: (Trend.First to "")
            val picked = entry.id in picks
            ListRow(
                title = Units.format(entry.grams, unit),
                subtitle = "${DateFmt.dayLabel(entry.timestampMs)} · ${DateFmt.clock(entry.timestampMs)}" +
                    "  ·  #${"%04d".format(numbers[entry.id] ?: 0)}",
                hue = afya.hue("weight"),
                icon = if (comparing && picked) AfyaIcons.Check else AfyaIcons.Weight,
                modifier = if (comparing) {
                    Modifier.semantics(mergeDescendants = true) { selected = picked }
                } else {
                    Modifier
                },
                trailing = {
                    if (trend == Trend.First) {
                        Text(
                            text = stringResource(R.string.first_weigh_in),
                            style = MaterialTheme.typography.labelMedium,
                            color = afya.inkMuted,
                        )
                    } else {
                        DeltaPill(trend = trend, text = delta, hue = afya.hue("weight"))
                    }
                },
                onClick = {
                    if (comparing) {
                        picks = when {
                            picked -> picks - entry.id
                            picks.size >= 2 -> listOf(picks.last(), entry.id)
                            else -> picks + entry.id
                        }
                    } else {
                        open = if (open == entry.id) -1L else entry.id
                    }
                },
            )
            if (!comparing && open == entry.id) {
                EntryDetail(entry, diagnostics, onDelete = { pendingDelete = entry })
            }
            if (index < page.lastIndex) Hairline(Modifier.padding(start = 62.dp))
        }
        if (entries.size > shown) {
            Hairline()
            TextAction(
                label = stringResource(R.string.records_show_more, minOf(PAGE, entries.size - shown)),
                onClick = { shown += PAGE },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // The same two-step the weigh-in sheet uses: the row opens, the word asks, the bar undoes.
    pendingDelete?.let { gone ->
        AfyaDialog(
            title = stringResource(R.string.delete_reading_title),
            message = stringResource(R.string.delete_reading_message),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDeleteReading(gone.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private const val PAGE = 30

/**
 * Two weigh-ins, earlier on the left. Composition is set beside composition only when both were read
 * through the same profile; otherwise the card says so and compares weight alone.
 */
@Composable
private fun CompareCard(
    earlier: ReadingsDb.Entry,
    later: ReadingsDb.Entry,
    unit: WeightUnit,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
) {
    val afya = MaterialTheme.afya
    val before = metricsOf(earlier)
    val after = metricsOf(later)
    val comparable = before != null && after != null &&
        Report.sameBasis(after, later.kilograms, before, earlier.kilograms)

    AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(0)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${DateFmt.dayLabel(earlier.timestampMs)} · ${DateFmt.clock(earlier.timestampMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = afya.inkMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(AfyaIcons.Chevron, contentDescription = null, tint = afya.inkMuted)
            Text(
                text = "${DateFmt.dayLabel(later.timestampMs)} · ${DateFmt.clock(later.timestampMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = afya.inkMuted,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Spacer(Modifier.height(8.dp))
        CompareLine(
            label = stringResource(R.string.tile_weight),
            before = Units.format(earlier.grams, unit),
            after = Units.format(later.grams, unit),
            change = deltaParts(later.kilograms - earlier.kilograms, unit).second,
        )
        if (comparable) {
            CompareFigures.forEach { (name, label) ->
                val a = Report.figureOf(name, before!!, unit) ?: return@forEach
                val b = Report.figureOf(name, after!!, unit) ?: return@forEach
                val suffix = if (name == "visceral") " cm²" else if (name == "bmi") "" else " %"
                val decimals = if (name == "visceral") 0 else 1
                val moved = b - a
                Hairline()
                CompareLine(
                    label = stringResource(label),
                    before = "%.${decimals}f".format(a) + suffix,
                    after = "%.${decimals}f".format(b) + suffix,
                    change = if (kotlin.math.abs(moved) < 0.05) {
                        stringResource(R.string.no_change)
                    } else {
                        (if (moved > 0) "+" else "−") + "%.${decimals}f".format(kotlin.math.abs(moved)) + suffix
                    },
                )
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.compare_rebased),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
        }
    }
}

private val CompareFigures = listOf(
    "bmi" to R.string.tile_bmi,
    "fat" to R.string.tile_fat,
    "muscle" to R.string.tile_muscle,
    "water" to R.string.tile_water,
    "visceral" to R.string.tile_visceral,
)

@Composable
private fun CompareLine(label: String, before: String, after: String, change: String) {
    val afya = MaterialTheme.afya
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = afya.inkMuted)
            Text(
                text = "$before  →  $after",
                style = MaterialTheme.typography.titleMedium,
                color = afya.ink,
            )
        }
        Text(change, style = MaterialTheme.typography.labelLarge, color = afya.ink)
    }
}

/** What one filed row actually holds, printed the way it was stored, and the way to take it back. */
@Composable
private fun EntryDetail(entry: ReadingsDb.Entry, diagnostics: Boolean, onDelete: () -> Unit) {
    val afya = MaterialTheme.afya
    val figures = listOfNotNull(
        entry.bmi?.let { "BMI ${"%.1f".format(it)}" },
        entry.fatPercent?.let { "fat ${"%.1f".format(it)}%" },
        entry.musclePercent?.let { "muscle ${"%.1f".format(it)}%" },
        entry.waterPercent?.let { "water ${"%.0f".format(it)}%" },
        entry.visceralCm2?.let { "visceral ${"%.0f".format(it)} cm²" },
        entry.impedanceOhms?.let { "${"%.0f".format(it)} Ω" },
    )
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        if (figures.isNotEmpty()) {
            Text(
                text = figures.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = afya.ink,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (diagnostics) {
            Text(
                text = "${entry.mac} · frame ${entry.frameName} · id ${entry.productId}",
                style = MonoBody,
                color = afya.inkMuted,
            )
        }
        Text(
            text = DateFmt.full(entry.timestampMs),
            style = MonoBody,
            color = afya.inkMuted,
        )
        // Destructive text actions take the high-band ink, never the brand.
        TextAction(
            label = stringResource(R.string.delete_reading),
            onClick = onDelete,
            tone = afya.bandHigh,
        )
    }
}

/**
 * A share and its mass are one figure said twice, so they print as one row: the percentage carries
 * the band, the mass rides in the subtitle. Rows with no sibling print as they are.
 */
private val MassOf = mapOf(
    "fat" to "fat_mass",
    "water" to "water_mass",
    "muscle" to "muscle_mass",
    "skeletal" to "skeletal_mass",
    "protein" to "protein_mass",
)

/**
 * The composition as a list. Every row says in words which band its figure falls in, in that band's
 * colour, and prints the range it was judged against — a bare number leaves people guessing.
 */
@Composable
private fun CompositionList(rows: List<ReportRow>, onSelect: (ReportRow) -> Unit) {
    val afya = MaterialTheme.afya
    val byName = rows.associateBy { it.name }
    val folded = MassOf.filterKeys { byName.containsKey(it) }.values.toSet()
    val shown = rows.filter { it.name !in folded }
    AfyaCard(contentPadding = 10.dp, modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, row ->
            val tone = afya.band(row.band)
            val mass = MassOf[row.name]?.let { byName[it] }
            val subtitle = buildString {
                append(row.reference)
                if (mass != null) append(" · ${mass.valueText} ${mass.unit}")
                if (row.hasChange) append(" · ${row.changeText}")
            }
            ListRow(
                title = row.title,
                subtitle = subtitle,
                hue = afya.hue(row.name),
                icon = metricIcon(row.name),
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(row.valueText, style = TileValue, color = tone)
                            if (row.unit.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(row.unit, style = FigureUnit, color = afya.inkMuted)
                            }
                        }
                        // A row with no range of its own is read against its sibling; a pill saying
                        // "no range" on every one of them would only be noise.
                        if (row.band != Band.Unknown) {
                            Spacer(Modifier.height(5.dp))
                            StatusPill(text = row.bandWord, tone = tone)
                        }
                    }
                },
                onClick = { onSelect(row) },
            )
            if (index < shown.lastIndex) Hairline(Modifier.padding(start = 62.dp))
        }
    }
}

private data class Month(val tab: String, val title: String, val entries: List<ReadingsDb.Entry>)

private fun groupMonths(newestFirst: List<ReadingsDb.Entry>): List<Month> {
    val grouped = LinkedHashMap<String, MutableList<ReadingsDb.Entry>>()
    newestFirst.forEach { entry ->
        grouped.getOrPut(DateFmt.monthKey(entry.timestampMs)) { mutableListOf() }.add(entry)
    }
    return grouped.values.map {
        Month(DateFmt.monthTab(it.first().timestampMs), DateFmt.month(it.first().timestampMs), it)
    }
}

@Composable
private fun changeText(entries: List<ReadingsDb.Entry>, unit: WeightUnit): String {
    if (entries.size < 2) return "—"
    val moved = (entries.first().grams - entries.last().grams) / 1000.0
    val (trend, text) = deltaParts(moved, unit)
    return if (trend == Trend.Flat) stringResource(R.string.no_change) else text
}

private fun averageText(entries: List<ReadingsDb.Entry>, unit: WeightUnit): String {
    if (entries.isEmpty()) return "--"
    val average = entries.map { it.grams }.average()
    return "${Units.valueText(average, unit)} ${unit.label}"
}
