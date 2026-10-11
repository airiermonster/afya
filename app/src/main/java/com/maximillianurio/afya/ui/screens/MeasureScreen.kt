package com.maximillianurio.afya.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.app.KeyFetch
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.Report
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.core.ScaleReading
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.Trend
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.WeightUnit
import com.maximillianurio.afya.core.WeightValue
import com.maximillianurio.afya.core.deltaParts
import com.maximillianurio.afya.data.ReadingsDb
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.AfyaDialog
import com.maximillianurio.afya.ui.components.BandBar
import com.maximillianurio.afya.ui.components.DeltaPill
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.MetricGrid
import com.maximillianurio.afya.ui.components.PrimaryAction
import com.maximillianurio.afya.ui.components.ProgressRing
import com.maximillianurio.afya.ui.components.SectionHead
import com.maximillianurio.afya.ui.components.StatusPill
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.components.TonalAction
import com.maximillianurio.afya.ui.components.ValueRow
import com.maximillianurio.afya.ui.components.bandSpan
import com.maximillianurio.afya.ui.components.enterLift
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.AfyaMotion
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.HeroNumber
import com.maximillianurio.afya.ui.theme.MonoBody
import com.maximillianurio.afya.ui.theme.SectionHeader
import com.maximillianurio.afya.ui.theme.afya
import com.maximillianurio.afya.ui.theme.reducedMotion
import kotlin.math.abs

/**
 * The platform read. The scale's own LCD figure is the primary readout and nothing replaces it; the
 * conversion to the profile unit is a secondary line. When the load settles, the ring sweeps to where
 * that weight sits, the figure counts up and the composition grid lifts in behind it.
 */
@Composable
fun MeasureScreen(
    state: AppState,
    previousTo: (Long) -> ReadingsDb.Entry?,
    recent: () -> List<ReadingsDb.Entry>,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
    onRecord: () -> Unit,
    onConfirmMine: () -> Unit,
    onNotMine: () -> Unit,
    onDeleteReading: (Long) -> Unit,
    onSelectMetric: (ReportRow) -> Unit,
    goToYou: () -> Unit,
    onOpenRecords: () -> Unit,
    onScan: () -> Unit,
    onRetryKey: () -> Unit,
    trendKg: Double? = null,
    permissionBlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    val live = state.reading

    // Someone standing on a scale is not touching their phone. The screen stays lit while the
    // platform is open so the scan, which stops with the screen, is still running when they step on.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // A scale goes quiet the moment weight comes off it. The figure that finished stays on the
    // platform, with its metrics, until the next one locks.
    val reading = if (live?.weight != null) live else (state.lastStable ?: live)
    val held = reading !== live
    val weight = reading?.weight
    val locked = reading?.locked == true
    val grams = weight?.grams
    val plausible = grams != null && BodyComposition.bodyPlausible(grams)
    val settled = locked && plausible

    // The row this figure was filed as. Filing happens on its own, so the only action left here is
    // taking it back.
    val filedRow = if (settled) state.filedEntry else null
    val filed = filedRow != null
    // Held back for being far from the recent pattern: nothing is read through the owner's profile
    // until they say the figure is theirs.
    val checkReferenceKg = if (settled && !filed) state.checkReferenceKg else null
    val checking = checkReferenceKg != null
    var confirmDelete by remember { mutableStateOf(false) }

    // A filed row prints the figures it was filed with; a fresh load is read through the equations now.
    val metrics: BodyMetrics? = filedRow?.let { metricsOf(it) }
        ?: if (plausible && profile.complete) {
            BodyComposition.compute(grams!! / 1000.0, profile.heightCm, profile.ageYears, profile.sex)
        } else {
            null
        }
    // With nothing on the plinth the row compares the two readings already on file.
    val last = state.lastSaved
    val comparedRow: ReadingsDb.Entry? = filedRow?.let { previousTo(it.timestampMs) }
        ?: last?.let { if (weight == null) previousTo(it.timestampMs) else it }
    val weightKg = filedRow?.kilograms ?: grams?.div(1000.0) ?: 0.0
    // A change is only printed between two readings taken through the same profile.
    val previous = comparedRow?.let { Report.comparable(metrics, weightKg, metricsOf(it), it.kilograms) }

    val rows = if (metrics != null && profile.complete && weightKg > 0.0 && !checking) {
        Report.rows(metrics, weightKg, profile.ageYears, profile.sex, profile.unit, previous)
    } else {
        emptyList()
    }
    val bmiRow = rows.firstOrNull { it.name == "bmi" }

    val statusText = stringResource(
        when {
            weight == null && !state.adapterEnabled -> R.string.status_bt_off
            weight == null && reading == null && !state.isScanning ->
                if (state.permissionsGranted) R.string.status_not_scanning else R.string.status_needs_permission
            weight == null && reading?.keyMissing == true ->
                if (state.keyFetch == KeyFetch.Failed) R.string.status_key_failed else R.string.status_key_needed
            reading == null -> R.string.conn_scanning
            weight == null -> R.string.status_wake
            !locked -> R.string.status_measuring
            !plausible -> R.string.status_not_a_body
            filed -> R.string.stamp_on_file
            checking -> R.string.status_check
            state.recordQueued -> R.string.status_measuring
            else -> R.string.stamp_ready
        }
    )
    val statusTone = when {
        weight == null && (!state.adapterEnabled || (reading == null && !state.isScanning)) -> afya.bandWarn
        weight == null && reading?.keyMissing == true ->
            if (state.keyFetch == KeyFetch.Failed) afya.bandWarn else afya.brand
        reading == null -> afya.brand
        weight == null -> afya.inkMuted
        !locked -> afya.brand
        !plausible -> afya.bandWarn
        filed -> afya.bandHealthy
        checking -> afya.bandWarn
        else -> afya.brand
    }

    val unitLine = when {
        weight == null -> ""
        Units.isChosenUnit(weight.unit, profile.unit) -> ""
        else -> stringResource(
            R.string.unit_equivalent,
            weight.unit.label,
            Units.valueText(weight.grams, profile.unit),
            profile.unit.label,
        )
    }

    val delta = comparedRow?.let { before ->
        val kg = (weight?.grams ?: last?.grams ?: 0.0) / 1000.0 - before.kilograms
        deltaParts(kg, profile.unit)
    } ?: (Trend.First to stringResource(R.string.first_weigh_in))

    // Frames arrive several times a second; the history only changes when a row is filed.
    val month = remember(state.dataVersion) { recent() }
    val best = bestNearGoal(month, profile.targetKg, profile.unit)
    val goalLine = if (profile.targetKg > 0) {
        "${Units.valueText(Units.toGrams(profile.targetKg, profile.unit), profile.unit)} ${profile.unit.label}"
    } else {
        stringResource(R.string.goal_unset)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
    ) {
        SectionHead(
            title = stringResource(R.string.measure_heading),
            action = { StatusPill(text = statusText, tone = statusTone) },
        )

        Spacer(Modifier.height(16.dp))
        PlatformCard(
            profileUnit = profile.unit.label,
            reading = reading,
            weight = weight,
            settled = settled,
            held = held,
            queued = state.recordQueued,
            filed = filed,
            checking = checking,
            unitLine = unitLine,
            bmiRow = bmiRow,
            sex = profile.sex,
            bluetoothReady = state.adapterEnabled,
            scanning = state.isScanning,
            permissionsGranted = state.permissionsGranted,
            permissionBlocked = permissionBlocked,
            keyFetch = state.keyFetch,
            keyReason = state.keyFetchReason,
            onScan = onScan,
            onRetryKey = onRetryKey,
        )

        Spacer(Modifier.height(14.dp))
        AfyaCard(tonal = true, modifier = Modifier.fillMaxWidth(), contentPadding = 8.dp) {
            ValueRow(label = stringResource(R.string.row_vs_last), value = delta.second)
            Hairline()
            // Against the smoothed weight, a heavy morning reads as the water swing it usually is.
            val nowKg = (weight?.grams ?: last?.grams)?.div(1000.0)
            if (trendKg != null && nowKg != null) {
                ValueRow(
                    label = stringResource(R.string.row_vs_trend),
                    value = deltaParts(nowKg - trendKg, profile.unit).second,
                )
                Hairline()
            }
            ValueRow(label = stringResource(R.string.row_best_30), value = best ?: stringResource(R.string.field_blank))
            Hairline()
            ValueRow(label = stringResource(R.string.row_goal), value = goalLine)
            // Nothing is on the plinth yet, so there is no filing to report on it.
            if (reading != null) {
                Hairline()
                ValueRow(
                    label = stringResource(R.string.row_filed),
                    value = filedRow?.let { DateFmt.serial(it.timestampMs) }
                        ?: stringResource(R.string.not_filed),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        when {
            checkReferenceKg != null && grams != null -> WhoseReading(
                grams = grams,
                referenceKg = checkReferenceKg,
                unit = profile.unit,
                onMine = onConfirmMine,
                onNotMine = onNotMine,
            )

            // The ritual ends on what was done, not on the way to undo it: a plain line saying the
            // weigh-in is on file, the way to see it, and the delete kept small underneath.
            filedRow != null -> {
                Text(
                    text = if (trendKg != null) {
                        stringResource(
                            R.string.measure_filed_trend,
                            DateFmt.clock(filedRow.timestampMs),
                            Units.format(trendKg * 1000.0, profile.unit),
                        )
                    } else {
                        stringResource(R.string.measure_filed, DateFmt.clock(filedRow.timestampMs))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = afya.ink,
                )
                Spacer(Modifier.height(12.dp))
                TonalAction(
                    label = stringResource(R.string.measure_open_records),
                    onClick = onOpenRecords,
                    icon = AfyaIcons.Records,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextAction(
                    label = stringResource(R.string.delete_reading),
                    onClick = { confirmDelete = true },
                    tone = afya.bandHigh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Auto-record files the read on its own, so recording by hand only appears where it is
            // still a real choice: auto-record off, or a figure that was just deleted.
            settled && !state.recordQueued -> PrimaryAction(
                label = stringResource(R.string.record_reading),
                onClick = onRecord,
                icon = AfyaIcons.Check,
            )
        }

        if (!profile.complete) {
            Spacer(Modifier.height(14.dp))
            AfyaCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.profile_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = afya.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    TonalAction(
                        label = stringResource(R.string.add_profile),
                        onClick = goToYou,
                        icon = AfyaIcons.You,
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            SectionHead(
                title = stringResource(R.string.home_measures_heading),
                action = {
                    TextAction(stringResource(R.string.measure_full_report), onClick = onOpenRecords)
                },
            )
            Spacer(Modifier.height(12.dp))
            MetricGrid(
                rows = HeadlineMetrics.mapNotNull { name -> rows.firstOrNull { it.name == name } },
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

        Spacer(Modifier.height(20.dp))
        if (reading != null && state.settings.diagnostics) DeviceLine(reading = reading)
    }

    if (confirmDelete && filedRow != null) {
        AfyaDialog(
            title = stringResource(R.string.delete_reading_title),
            message = stringResource(R.string.delete_reading_message),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteReading(filedRow.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The ring, the device's own figure inside it, and what can still be done with it. The ring holds at
 * zero while the load is unsettled and springs to its position the frame the scale locks, then stays
 * there once weight comes off, because the figure and its metrics are kept until the next weigh-in.
 */
@Composable
private fun PlatformCard(
    profileUnit: String,
    reading: ScaleReading?,
    weight: WeightValue?,
    settled: Boolean,
    held: Boolean,
    queued: Boolean,
    filed: Boolean,
    checking: Boolean,
    unitLine: String,
    bmiRow: ReportRow?,
    sex: Sex,
    bluetoothReady: Boolean,
    scanning: Boolean,
    permissionsGranted: Boolean,
    permissionBlocked: Boolean,
    keyFetch: KeyFetch,
    keyReason: String?,
    onScan: () -> Unit,
    onRetryKey: () -> Unit,
) {
    val afya = MaterialTheme.afya
    val reduced = reducedMotion()
    val lcd = weight?.display ?: "--"
    val keyMissing = weight == null && reading?.keyMissing == true
    val blockerShown = weight == null &&
        (!bluetoothReady || keyMissing || (reading == null && !scanning))

    // The authored moment: the frame locks, the ring closes, and the figure the scale itself shows
    // takes one small breath. Nothing counts up — every digit on screen is one the LCD displayed.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(settled, lcd, reduced) {
        if (settled && !reduced) {
            pulse.snapTo(1f)
            pulse.animateTo(1.05f, tween(140, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, AfyaMotion.sweep())
        }
    }

    AfyaCard(
        modifier = Modifier
            .fillMaxWidth()
            .enterLift(0),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !bluetoothReady -> BlockerPanel(
                    image = AfyaIcons.Bluetooth,
                    title = stringResource(R.string.status_bt_off),
                    body = stringResource(R.string.blocker_bt_body),
                    actionLabel = stringResource(R.string.action_bt_on),
                    onAction = onScan,
                )

                keyMissing -> KeyPanel(
                    fetching = keyFetch == KeyFetch.Fetching,
                    reason = keyReason,
                    onRetryKey = onRetryKey,
                )

                reading == null && !scanning && !permissionsGranted -> BlockerPanel(
                    image = AfyaIcons.Bluetooth,
                    title = stringResource(R.string.blocker_permission_title),
                    body = stringResource(
                        if (permissionBlocked) R.string.blocker_permission_settings_body
                        else R.string.blocker_permission_body
                    ),
                    actionLabel = stringResource(
                        if (permissionBlocked) R.string.action_open_settings else R.string.action_allow
                    ),
                    onAction = onScan,
                )

                reading == null && !scanning -> BlockerPanel(
                    image = AfyaIcons.Scan,
                    title = stringResource(R.string.status_not_scanning),
                    body = stringResource(R.string.blocker_scan_body),
                    actionLabel = stringResource(R.string.scan_start),
                    onAction = onScan,
                )

                weight == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The scale drifts while there is nothing on the plinth. A blocker with a face
                    // of its own takes the stage before this branch is ever reached.
                    val bob = if (reduced) 0f else rememberInfiniteTransition(label = "scale-idle")
                        .animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2_600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "scale-drift",
                        ).value
                    Image(
                        painter = painterResource(R.drawable.hero_scale),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = 224.dp, height = 180.dp)
                            .graphicsLayer {
                                translationY = -9.dp.toPx() * (bob * 2f - 1f)
                            },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.measure_idle_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = afya.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.measure_wake_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = afya.inkMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    // The ring is the scale's state, not a figure: turning while the load settles,
                    // closed once it locks. Where the weight sits is the BMI bar under the card.
                    ProgressRing(
                        fraction = if (settled) 1f else 0f,
                        hue = if (checking) afya.bandWarn else afya.brand,
                        ringSize = 236.dp,
                        stroke = 15.dp,
                        spring = true,
                        settling = !settled,
                        content = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.semantics(mergeDescendants = true) { },
                            ) {
                                Text(
                                    text = lcd,
                                    style = fitInside(lcd, HeroNumber, RingFigureWidth),
                                    color = afya.ink,
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = pulse.value
                                        scaleY = pulse.value
                                    },
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = weight?.unit?.label ?: profileUnit,
                                    style = FigureUnit,
                                    color = afya.brand,
                                )
                                Spacer(Modifier.height(10.dp))
                                StatusPill(
                                    text = when {
                                        filed -> stringResource(R.string.stamp_on_file)
                                        checking -> stringResource(R.string.status_check)
                                        !settled || queued -> stringResource(R.string.status_measuring)
                                        else -> stringResource(R.string.stamp_ready)
                                    },
                                    tone = when {
                                        filed -> afya.bandHealthy
                                        checking -> afya.bandWarn
                                        else -> afya.brand
                                    },
                                    // Spoken when it changes: settling, ready, on file. The figure
                                    // itself moves every frame and would talk over the person.
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        },
                    )
                }
            }
        }

        if (unitLine.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = unitLine,
                style = MaterialTheme.typography.bodyMedium,
                color = afya.inkMuted,
            )
        }

        if (settled && bmiRow != null) {
            Spacer(Modifier.height(18.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.measure_where_it_sits),
                    style = SectionHeader,
                    color = afya.inkMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${stringResource(R.string.tile_bmi)} ${bmiRow.valueText} · ${bmiRow.bandWord}",
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.band(bmiRow.band),
                )
            }
            Spacer(Modifier.height(10.dp))
            BandBar(
                position = bmiRow.position,
                tone = afya.band(bmiRow.band),
                range = bandSpan("bmi", sex) ?: (0.2f to 0.5f),
            )
        }

        // The idle panel already says what to do; this line only narrates a figure that exists.
        if (!blockerShown && weight != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    when {
                        held && settled -> R.string.measure_done_hint
                        !settled -> R.string.measure_step_hint
                        else -> R.string.measure_locked_hint
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
        }
    }
}

/**
 * A thing that is standing between the person and a reading: what it is, in words, and the one
 * action that removes it. The idle drift never wears one of these masks.
 */
@Composable
private fun BlockerPanel(
    image: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val afya = MaterialTheme.afya
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Icon(image, contentDescription = null, tint = afya.brand, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = afya.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = afya.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        TonalAction(label = actionLabel, onClick = onAction)
    }
}

/** The key lookup as it happens: the wait, the plain reason when it fails, and one way to retry. */
@Composable
private fun KeyPanel(
    fetching: Boolean,
    reason: String?,
    onRetryKey: () -> Unit,
) {
    val afya = MaterialTheme.afya
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        if (fetching) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = afya.brand,
                strokeWidth = 3.dp,
            )
        } else {
            Icon(AfyaIcons.Lock, contentDescription = null, tint = afya.brand, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.key_panel_title),
            style = MaterialTheme.typography.titleMedium,
            color = afya.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                fetching -> stringResource(R.string.key_fetch_body)
                reason != null -> reason
                else -> stringResource(R.string.key_wait_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = afya.inkMuted,
            textAlign = TextAlign.Center,
        )
        if (!fetching) {
            Spacer(Modifier.height(18.dp))
            TonalAction(
                label = stringResource(R.string.action_retry),
                onClick = onRetryKey,
                icon = AfyaIcons.Restore,
            )
        }
    }
}

/**
 * A locked figure far from the recent pattern, held back from the records. The figure itself stays on
 * the platform above for whoever is standing on the scale; this only asks whose records it belongs in.
 */
@Composable
private fun WhoseReading(
    grams: Double,
    referenceKg: Double,
    unit: WeightUnit,
    onMine: () -> Unit,
    onNotMine: () -> Unit,
) {
    val afya = MaterialTheme.afya
    val gap = abs(grams / 1000.0 - referenceKg) * 1000.0
    AfyaCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.guest_check_title),
            style = MaterialTheme.typography.titleMedium,
            color = afya.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.guest_check_body,
                Units.format(grams, unit),
                Units.format(gap, unit),
                Units.format(referenceKg * 1000.0, unit),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = afya.inkMuted,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalAction(
                label = stringResource(R.string.guest_not_me),
                onClick = onNotMine,
                modifier = Modifier.weight(1f),
            )
            PrimaryAction(
                label = stringResource(R.string.guest_mine),
                onClick = onMine,
                icon = AfyaIcons.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The raw sweep, for the reader who asked for it: MAC, frame and impedance are protocol output. */
@Composable
private fun DeviceLine(reading: ScaleReading) {
    val afya = MaterialTheme.afya
    val impedance = reading.impedanceOhms?.let { " · %.0f Ω".format(it) }.orEmpty()
    Text(
        text = "${reading.mac} · ${reading.frameName}$impedance",
        style = MonoBody,
        color = afya.inkMuted,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = reading.rawHex,
        style = MonoBody,
        color = afya.inkMuted,
    )
}

/** The nearest read to the goal in the last thirty days; an empty goal has nothing to be near. */
private fun bestNearGoal(
    entries: List<ReadingsDb.Entry>,
    targetKg: Double,
    unit: com.maximillianurio.afya.core.WeightUnit,
): String? {
    if (targetKg <= 0) return null
    val cutoff = System.currentTimeMillis() - 30 * 86_400_000L
    val best = entries
        .filter { it.timestampMs >= cutoff && BodyComposition.bodyPlausible(it.grams) }
        .minByOrNull { abs(it.kilograms - targetKg) }
        ?: return null
    return "${Units.format(best.grams, unit)} · ${DateFmt.dayLabel(best.timestampMs)}"
}

/** The widest the figure may run inside the 236 dp ring once its 15 dp stroke and a margin are off. */
private val RingFigureWidth = 176.dp

/**
 * [style] at the largest size, no bigger than its own, at which [text] fits in [maxWidth] on one line.
 * A five-digit LCD reading or a large system font would otherwise run over the ring.
 */
@Composable
private fun fitInside(text: String, style: TextStyle, maxWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val limit = with(density) { maxWidth.roundToPx() }
    return remember(text, style, limit, density.fontScale) {
        var size = style.fontSize.value
        var fitted = style
        while (size > 24f) {
            fitted = style.copy(fontSize = size.sp, lineHeight = (size * 1.03f).sp)
            if (measurer.measure(text, fitted, maxLines = 1).size.width <= limit) break
            size -= 2f
        }
        fitted
    }
}
