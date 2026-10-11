package com.maximillianurio.afya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.MeasurementSite
import com.maximillianurio.afya.core.MeasurementSites
import com.maximillianurio.afya.core.Side
import com.maximillianurio.afya.data.ReadingsDb
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.ListRow
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.TileValue
import com.maximillianurio.afya.ui.theme.afya

/**
 * The tape measurements, kept next to the weigh-ins because they are read the same way: a figure the
 * person wrote in, with its date. Values are always stored in centimetres; the cm/in toggle only
 * changes how they are printed and typed.
 */
@Composable
fun MeasuresSection(
    state: AppState,
    measurements: () -> Map<String, ReadingsDb.Measurement>,
    onSave: (String, Double) -> Boolean,
    onDelete: (Long) -> Unit,
    onUnits: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val inches = state.settings.lengthInches
    val rows = remember { fieldRows() }
    val filed = remember(measurements, state.dataVersion) { measurements() }
    var editing by remember { mutableStateOf<MeasurementSite?>(null) }
    val taken = rows.count { row -> row.sites.any { it.key in filed } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.measures_rows, taken, rows.size),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
                modifier = Modifier.weight(1f),
            )
            TextAction(
                label = stringResource(R.string.unit_cm),
                onClick = { onUnits(false) },
                tone = if (!inches) afya.brand else afya.inkMuted,
            )
            Spacer(Modifier.width(4.dp))
            TextAction(
                label = stringResource(R.string.unit_in),
                onClick = { onUnits(true) },
                tone = if (inches) afya.brand else afya.inkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.measures_note),
            style = MaterialTheme.typography.bodySmall,
            color = afya.inkMuted,
        )
        Spacer(Modifier.height(12.dp))

        AfyaCard(contentPadding = 10.dp, modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                when (row.sites.size) {
                    1 -> SiteLine(
                        site = row.sites.first(),
                        existing = filed[row.sites.first().key],
                        inches = inches,
                        onEdit = { editing = row.sites.first() },
                    )

                    else -> PairedSites(row.sites, filed, inches) { editing = it }
                }
                if (index < rows.lastIndex) Hairline(Modifier.padding(horizontal = 10.dp))
            }
        }
    }

    val site = editing
    if (site != null) {
        EditFieldDialog(
            site = site,
            existing = filed[site.key],
            inches = inches,
            onSave = { value -> onSave(site.key, value) },
            onDelete = { id ->
                onDelete(id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** One body site as a row: the label, the figure, and the day it was written in. */
@Composable
private fun SiteLine(
    site: MeasurementSite,
    existing: ReadingsDb.Measurement?,
    inches: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val parts = existing?.let { MeasurementSites.lengthText(it.centimetres, inches) }
    ListRow(
        title = site.fieldLabel,
        subtitle = existing?.let {
            "${DateFmtLabel(it.timestampMs)}"
        } ?: stringResource(R.string.field_blank),
        hue = afya.hue("weight"),
        icon = siteIcon(site),
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = parts?.first ?: stringResource(R.string.field_blank),
                    style = TileValue,
                    color = if (parts == null) afya.inkMuted else afya.ink,
                )
                if (parts != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(parts.second, style = FigureUnit, color = afya.inkMuted)
                }
            }
        },
        onClick = onEdit,
    )
}

/** A bilateral row: one group name, then the two sides as half-width wells. */
@Composable
private fun PairedSites(
    sites: List<MeasurementSite>,
    filed: Map<String, ReadingsDb.Measurement>,
    inches: Boolean,
    onEdit: (MeasurementSite) -> Unit,
) {
    val afya = MaterialTheme.afya
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = siteIcon(sites.first()),
                contentDescription = null,
                tint = afya.hue("weight"),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(sites.first().label, style = MaterialTheme.typography.titleSmall, color = afya.ink)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sites.forEach { site ->
                val existing = filed[site.key]
                val parts = existing?.let { MeasurementSites.lengthText(it.centimetres, inches) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(afya.cardTint)
                        .clickable { onEdit(site) }
                        .padding(14.dp),
                ) {
                    Text(
                        text = site.side.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = afya.inkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = parts?.first ?: stringResource(R.string.field_blank),
                            style = TileValue,
                            color = if (parts == null) afya.inkMuted else afya.ink,
                        )
                        if (parts != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(parts.second, style = FigureUnit, color = afya.inkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Side.label(): String = when (this) {
    Side.Left -> stringResource(R.string.side_left)
    Side.Right -> stringResource(R.string.side_right)
    Side.None -> ""
}

/**
 * The glyph for a tape site: the part of the body the tape goes round, not a ruler on eleven rows.
 * Bilateral keys share one glyph, taken from the base name before the side suffix.
 */
private fun siteIcon(site: MeasurementSite): ImageVector =
    when (site.key.removeSuffix("_l").removeSuffix("_r")) {
        "neck" -> AfyaIcons.Necklace
        "chest" -> AfyaIcons.TankTop
        "waist" -> AfyaIcons.Belt
        "navel" -> AfyaIcons.SixPack
        "hip" -> AfyaIcons.Shorts
        "buttock" -> AfyaIcons.BackMuscle
        "arm" -> AfyaIcons.Skeletal
        "forearm" -> AfyaIcons.Hand
        "wrist" -> AfyaIcons.Watch
        "thigh" -> AfyaIcons.Leg
        else -> AfyaIcons.Socks
    }

/** Sites that share a body part print as one row; everything else stands alone. */
private data class FieldRow(val sites: List<MeasurementSite>)

private fun fieldRows(): List<FieldRow> =
    MeasurementSites.all
        .groupBy { it.key.removeSuffix("_l").removeSuffix("_r") }
        .values.map { FieldRow(it) }

/**
 * A figure typed in the unit on screen, written back as centimetres. A rejected value leaves the
 * dialog open with the text intact, because the notice explaining why lands behind it.
 */
@Composable
private fun EditFieldDialog(
    site: MeasurementSite,
    existing: ReadingsDb.Measurement?,
    inches: Boolean,
    onSave: (Double) -> Boolean,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val afya = MaterialTheme.afya
    var text by remember(site.key, existing?.centimetres, inches) {
        mutableStateOf(existing?.let { MeasurementSites.lengthText(it.centimetres, inches).first } ?: "")
    }
    val unit = if (inches) stringResource(R.string.unit_in) else stringResource(R.string.unit_cm)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        titleContentColor = afya.ink,
        textContentColor = afya.inkMuted,
        title = { Text(stringResource(R.string.edit_field, site.fieldLabel)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text(stringResource(R.string.edit_field_hint, unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing.id) }) {
                        Text(stringResource(R.string.remove_field), color = afya.bandHigh)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toDoubleOrNull() ?: return@TextButton
                if (onSave(MeasurementSites.toCentimetres(value, inches))) onDismiss()
            }) {
                Text(stringResource(R.string.save_field), color = afya.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = afya.inkMuted) }
        },
    )
}

private fun DateFmtLabel(millis: Long): String =
    com.maximillianurio.afya.core.DateFmt.let { "${it.dayLabel(millis)} · ${it.clock(millis)}" }
