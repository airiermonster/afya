package com.maximillianurio.afya.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.core.Band
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.Trend
import com.maximillianurio.afya.ui.theme.FigureNumber
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.afya

/**
 * One figure read properly, over time: where it sits now, the line it has drawn across every filed
 * weigh-in, and the sentence saying what it is. Opened from any tile or report row, over whatever
 * screen the person was on, so they never lose their place to read a history.
 *
 * [history] is (time, figure) in ascending time, in the unit [row] prints in. Each point is the
 * snapshot as it was filed, so the line is what the report said on each day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricHistorySheet(
    row: ReportRow,
    history: List<Pair<Long, Double>>,
    sex: Sex,
    onDismiss: () -> Unit,
    leftOut: Int = 0,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue(row.name)
    val tone = afya.band(row.band)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val decimals = if (row.name == "bone") 2 else if (row.name in WholeFigures) 0 else 1
    fun print(value: Double) = "%.${decimals}f".format(value) + if (row.unit.isEmpty()) "" else " ${row.unit}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = afya.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(image = metricIcon(row.name), hue = hue, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = afya.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (row.band != Band.Unknown) {
                        Text(row.bandWord, style = MaterialTheme.typography.bodyMedium, color = tone)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(row.valueText, style = FigureNumber, color = afya.ink)
                if (row.unit.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(row.unit, style = FigureUnit, color = afya.inkMuted)
                }
                if (row.hasChange || row.trend == Trend.Flat) {
                    DeltaPill(trend = row.trend, text = row.changeText, hue = hue)
                }
            }

            if (row.band != Band.Unknown) {
                Spacer(Modifier.height(14.dp))
                BandBar(position = row.position, tone = tone, range = bandSpan(row.name, sex))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_reference, row.reference),
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.inkMuted,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.history_heading),
                style = MaterialTheme.typography.titleMedium,
                color = afya.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            TrendArea(
                points = history,
                hue = hue,
                chartHeight = 150.dp,
                startLabel = history.firstOrNull()?.let { DateFmt.dayLabel(it.first) },
                endLabel = history.lastOrNull()?.let { DateFmt.dayLabel(it.first) },
                emptyLabel = stringResource(R.string.history_low_data),
                description = history.takeIf { it.size >= 2 }?.let {
                    stringResource(
                        R.string.history_chart_description,
                        row.title,
                        it.size,
                        print(it.first().second),
                        print(it.last().second),
                    )
                },
            )
            if (leftOut > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.history_left_out, leftOut, leftOut),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
            if (history.size >= 2) {
                Spacer(Modifier.height(8.dp))
                ValueRow(label = stringResource(R.string.history_low), value = print(history.minOf { it.second }))
                Hairline()
                ValueRow(label = stringResource(R.string.history_high), value = print(history.maxOf { it.second }))
                Hairline()
                ValueRow(
                    label = stringResource(R.string.history_count),
                    value = history.size.toString(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(row.explainer, style = MaterialTheme.typography.bodyMedium, color = afya.inkMuted)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.estimates_note),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
        }
    }
}

/** Rows printed without decimals: energy, body age and visceral area. */
private val WholeFigures = setOf("bmr", "metabolic_age", "visceral")
