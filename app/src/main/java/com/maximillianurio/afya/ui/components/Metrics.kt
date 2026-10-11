package com.maximillianurio.afya.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.Trend
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.SectionHeader
import com.maximillianurio.afya.ui.theme.TileValue
import com.maximillianurio.afya.ui.theme.afya

/**
 * The figure components every screen is built from. Two colour channels meet here: the metric's hue
 * identifies *which* figure it is, and the band colour says how it sits — so a tile stays readable
 * when the band word is the only status signal left.
 */

/** The glyph a metric is drawn with. It names the figure; it never judges it. */
fun metricIcon(name: String): ImageVector = when (name) {
    "weight", "ideal_weight", "healthy_range" -> AfyaIcons.Weight
    "bmi" -> AfyaIcons.Gauge
    "fat", "fat_mass" -> AfyaIcons.Percent
    "water", "water_mass" -> AfyaIcons.Droplet
    "muscle", "muscle_mass", "skeletal", "skeletal_mass", "lean" -> AfyaIcons.Activity
    "protein", "protein_mass" -> AfyaIcons.Molecule
    "bone" -> AfyaIcons.Shield
    "visceral", "obesity_degree" -> AfyaIcons.Alert
    "bmr" -> AfyaIcons.Flame
    "metabolic_age" -> AfyaIcons.Clock
    else -> AfyaIcons.Gauge
}

/**
 * The axis a row's band is drawn against. A mass row is judged by its percentage sibling, which is
 * how the report already bands it, so the marker and the printed reference agree.
 */
fun bandSpan(name: String, sex: Sex): Pair<Float, Float>? {
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

/** Which way a figure moved, drawn rather than typed — no glyph characters in the readout. */
@Composable
fun DeltaPill(
    trend: Trend,
    text: String,
    hue: Color,
    modifier: Modifier = Modifier,
) {
    if (trend == Trend.First || text.isEmpty()) return
    val afya = MaterialTheme.afya
    val icon = when (trend) {
        Trend.Up -> AfyaIcons.TrendUp
        Trend.Down -> AfyaIcons.TrendDown
        else -> AfyaIcons.Steady
    }
    Row(
        modifier = modifier.height(28.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size(15.dp))
        Text(
            text = if (trend == Trend.Flat) stringResource(R.string.no_change) else text,
            style = MaterialTheme.typography.labelMedium,
            color = afya.inkMuted,
        )
    }
}

/**
 * The two-up figure tile: glyph, name, value, where it sits. The whole tile is the tap target.
 *
 * A share of a whole is drawn as a ring (its fill is the percentage), a figure read against a
 * reference is drawn as a band bar (its marker is the position). Two indicators, but each one is the
 * only reading that makes sense for its kind of number.
 */
@Composable
fun MetricTile(
    row: ReportRow,
    sex: Sex,
    modifier: Modifier = Modifier,
    index: Int = 0,
    onClick: () -> Unit,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue(row.name)
    val tone = afya.band(row.band)
    val share = row.unit == "%"
    AfyaCard(
        modifier = modifier
            .fillMaxHeight()
            .enterLift(index)
            .clickable(onClick = onClick),
        contentPadding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(image = metricIcon(row.name), hue = hue, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                color = afya.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (share) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = row.valueText.toFloatOrNull()?.div(100f) ?: 0f,
                    hue = hue,
                    ringSize = 44.dp,
                    stroke = 5.dp,
                    content = {
                        Text(
                            text = row.valueText,
                            style = MaterialTheme.typography.labelSmall,
                            color = afya.ink,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${row.valueText} ${row.unit}",
                        style = TileValue,
                        color = afya.ink,
                    )
                    Text(row.bandWord, style = SectionHeader, color = tone)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(row.valueText, style = TileValue, color = afya.ink)
                if (row.unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(row.unit, style = FigureUnit, color = afya.inkMuted)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.bandWord,
                    style = SectionHeader,
                    color = tone,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BandBar(position = row.position, tone = tone, range = bandSpan(row.name, sex))
    }
}

/** The measures as a two-up grid. Rows are paired so the tiles keep the same height. */
@Composable
fun MetricGrid(
    rows: List<ReportRow>,
    sex: Sex,
    onSelect: (ReportRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.chunked(2).forEachIndexed { pairIndex, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { slot, row ->
                    MetricTile(
                        row = row,
                        sex = sex,
                        index = pairIndex * 2 + slot,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(row) },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A figure carried by a ring rather than a bar: value inside, name under. */
data class RingStat(
    val label: String,
    val valueText: String,
    val unit: String,
    val fraction: Float,
    val hueName: String,
)

@Composable
fun RingRow(
    stats: List<RingStat>,
    modifier: Modifier = Modifier,
    ringSize: Dp = 78.dp,
    durationMillis: Int = 900,
) {
    val afya = MaterialTheme.afya
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProgressRing(
                    fraction = stat.fraction,
                    hue = afya.hue(stat.hueName),
                    ringSize = ringSize,
                    stroke = 8.dp,
                    durationMillis = durationMillis,
                    content = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = stat.valueText,
                                style = MaterialTheme.typography.titleMedium,
                                color = afya.ink,
                            )
                            if (stat.unit.isNotEmpty()) {
                                Spacer(Modifier.width(2.dp))
                                Text(stat.unit, style = FigureUnit, color = afya.inkMuted)
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A label and a stored figure, for the pages that read a record back rather than chart it. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = afya.inkMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = afya.ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
