package com.maximillianurio.afya.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.Report
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.Trend
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.deltaParts
import com.maximillianurio.afya.data.ReadingsDb
import com.maximillianurio.afya.ui.components.AfyaAvatar
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.DeltaPill
import com.maximillianurio.afya.ui.components.EmptyState
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.ListRow
import com.maximillianurio.afya.ui.components.MetricGrid
import com.maximillianurio.afya.ui.components.PrimaryAction
import com.maximillianurio.afya.ui.components.Scene
import com.maximillianurio.afya.ui.components.SectionHead
import com.maximillianurio.afya.ui.components.StatusPill
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.HeroNumber
import com.maximillianurio.afya.ui.theme.afya
import java.util.Calendar

/** The measures the grid opens with before the intent is answered; `Aim.lead` reorders them. */
internal val HeadlineMetrics = listOf("bmi", "fat", "muscle", "water", "visceral", "protein", "bone", "metabolic_age")

/**
 * The page you land on. Everything already stored is on screen immediately — the latest reading at
 * full scale, the composition rings, the metric grid, recent weigh-ins — because opening the app and
 * being asked to step on a scale first is how the old build lost people.
 */
@Composable
fun HomeScreen(
    state: AppState,
    lastEntry: ReadingsDb.Entry?,
    previousEntry: ReadingsDb.Entry?,
    trendKg: Double?,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
    recent: () -> List<ReadingsDb.Entry>,
    onSelectMetric: (ReportRow) -> Unit,
    onEntryClick: (ReadingsDb.Entry) -> Unit,
    onMeasure: () -> Unit,
    onConnectionClick: () -> Unit,
    goToYou: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    val now = remember { System.currentTimeMillis() }

    val rows: List<ReportRow> = remember(lastEntry, previousEntry, state.dataVersion, profile.unit) {
        val metrics = lastEntry?.let { metricsOf(it) }
        if (metrics == null || !profile.complete) {
            emptyList()
        } else {
            Report.rows(
                metrics = metrics,
                weightKg = lastEntry!!.kilograms,
                ageYears = profile.ageYears,
                sex = profile.sex,
                unit = profile.unit,
                previous = previousEntry?.let {
                    Report.comparable(metrics, lastEntry.kilograms, metricsOf(it), it.kilograms)
                },
            )
        }
    }

    val lines = remember(recent, state.dataVersion) { recent().take(3) }
    val weighedToday = remember(state.lastSaved) {
        state.lastSaved?.let { DateFmt.dayBounds(now).first <= it.timestampMs } == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AfyaAvatar(
                name = profile.name,
                fileName = profile.avatarFile,
                size = 48.dp,
                onClick = goToYou,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting(now, profile.name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = afya.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                ConnectionChip(state = state, onClick = onConnectionClick)
            }
        }

        Spacer(Modifier.height(16.dp))
        HeroCard(
            state = state,
            entry = lastEntry,
            previous = previousEntry,
            trendKg = trendKg,
            weighedToday = weighedToday,
            onMeasure = onMeasure,
        )

        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHead(
                title = stringResource(R.string.home_measures_heading),
                action = { TextAction(stringResource(R.string.home_see_all), onClick = onSeeAll) },
            )
            Spacer(Modifier.height(12.dp))
            // Four figures, chosen by what the person said they are after; the rest are one tap away.
            MetricGrid(
                rows = (profile.intent.aim?.lead ?: HeadlineMetrics)
                    .mapNotNull { name -> rows.firstOrNull { it.name == name } }
                    .take(HOME_TILES),
                sex = profile.sex,
                onSelect = onSelectMetric,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.estimates_note),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
        }

        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHead(
                title = stringResource(R.string.home_recent_heading),
                action = { TextAction(stringResource(R.string.home_see_all), onClick = onSeeAll) },
            )
            Spacer(Modifier.height(4.dp))
            AfyaCard(contentPadding = 8.dp, modifier = Modifier.fillMaxWidth()) {
                lines.forEachIndexed { index, entry ->
                    val older = lines.getOrNull(index + 1)
                        ?: if (index == 0) previousEntry else null
                    ListRow(
                        title = Units.format(entry.grams, profile.unit),
                        subtitle = "${DateFmt.dayLabel(entry.timestampMs)} · ${DateFmt.clock(entry.timestampMs)}",
                        hue = afya.hue("weight"),
                        icon = AfyaIcons.Weight,
                        trailing = older?.let {
                            {
                                val (trend, text) = deltaParts(entry.kilograms - it.kilograms, profile.unit)
                                DeltaPill(trend = trend, text = text, hue = afya.hue("weight"))
                            }
                        },
                        onClick = { onEntryClick(entry) },
                    )
                    if (index < lines.lastIndex) Hairline(Modifier.padding(start = 62.dp))
                }
            }
        }
    }
}

private const val HOME_TILES = 4

/** The one line that says whether the app can see the scale at all. */
@Composable
private fun ConnectionChip(state: AppState, onClick: () -> Unit) {
    val afya = MaterialTheme.afya
    val reading = state.reading
    val (label, tone) = when {
        !state.adapterEnabled -> stringResource(R.string.status_bt_off) to afya.bandHigh
        reading != null && reading.locked && reading.weight != null ->
            stringResource(R.string.conn_locked) to afya.bandHealthy

        reading?.weight != null -> stringResource(R.string.status_measuring) to afya.brand
        state.isScanning -> stringResource(R.string.conn_scanning) to afya.brand
        !state.permissionsGranted -> stringResource(R.string.status_needs_permission) to afya.bandWarn
        else -> stringResource(R.string.conn_idle) to afya.inkMuted
    }
    StatusPill(
        text = label,
        tone = tone,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = stringResource(R.string.conn_open_scale), role = Role.Button, onClick = onClick),
    )
}

/**
 * The latest reading at the size it deserves, with the change against the one before it and the
 * three shares of the body that carry the most information.
 */
@Composable
private fun HeroCard(
    state: AppState,
    entry: ReadingsDb.Entry?,
    previous: ReadingsDb.Entry?,
    trendKg: Double?,
    weighedToday: Boolean,
    onMeasure: () -> Unit,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile

    if (entry == null) {
        AfyaCard(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                scene = Scene.FirstWeighIn,
                action = {
                    PrimaryAction(
                        label = stringResource(R.string.home_measure_cta),
                        onClick = onMeasure,
                        icon = AfyaIcons.Measure,
                    )
                },
            )
            if (!profile.complete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profile_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        }
        return
    }

    val (trend, delta) = previous?.let {
        deltaParts(entry.kilograms - it.kilograms, profile.unit)
    } ?: (Trend.First to "")

    AfyaCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.home_latest,
                        DateFmt.dayLabel(entry.timestampMs),
                        DateFmt.clock(entry.timestampMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Units.valueText(entry.grams, profile.unit),
                        style = HeroNumber,
                        color = afya.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = profile.unit.label,
                        style = FigureUnit,
                        color = afya.brand,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .background(afya.brandWash, RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                if (trend != Trend.First) {
                    Spacer(Modifier.height(6.dp))
                    DeltaPill(trend = trend, text = delta, hue = afya.hue("weight"))
                }
                if (trendKg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.home_trend,
                            Units.format(trendKg * 1000.0, profile.unit),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = afya.inkMuted,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.hero_composition),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 76.dp, height = 132.dp),
            )
        }

        // The day's one job, inside the card that shows the last time it was done.
        if (!weighedToday) {
            Spacer(Modifier.height(18.dp))
            PrimaryAction(
                label = stringResource(R.string.home_measure_cta),
                onClick = onMeasure,
                icon = AfyaIcons.Measure,
            )
        }
        if (!profile.complete) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.profile_needed),
                style = MaterialTheme.typography.bodySmall,
                color = afya.bandWarn,
            )
        }
    }
}

/** The greeting is a clock reading, not a claim about the person. */
@Composable
private fun greeting(nowMs: Long, name: String): String {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
    val salutation = stringResource(
        when (hour) {
            in 5..11 -> R.string.greet_morning
            in 12..16 -> R.string.greet_afternoon
            in 17..21 -> R.string.greet_evening
            else -> R.string.greet_late
        }
    )
    val first = name.trim().substringBefore(' ').takeIf { it.isNotBlank() }
    return if (first == null) salutation else stringResource(R.string.greet_named, salutation, first)
}
