package com.maximillianurio.afya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.BuildConfig
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.ReminderTime
import com.maximillianurio.afya.core.Sex
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.WeightUnit
import com.maximillianurio.afya.data.NetworkCall
import com.maximillianurio.afya.ui.brand.AfyaLockup
import com.maximillianurio.afya.ui.components.AfyaAvatar
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.BandBar
import com.maximillianurio.afya.ui.components.ChoiceGroup
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.IconTile
import com.maximillianurio.afya.ui.components.ListRow
import com.maximillianurio.afya.ui.components.PrimaryAction
import com.maximillianurio.afya.ui.components.SectionHead
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.components.ToggleRow
import com.maximillianurio.afya.ui.components.TonalAction
import com.maximillianurio.afya.ui.components.ValueRow
import com.maximillianurio.afya.ui.components.enterLift
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureNumber
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.MonoBody
import com.maximillianurio.afya.ui.theme.SectionHeader
import com.maximillianurio.afya.ui.theme.afya
import java.time.LocalDate

/**
 * The one page that holds the person and the machine: the inputs every figure is worked from, then the
 * binding, recording, display and data settings. Each half prints its own section heading; this is the
 * page that scrolls.
 */
@Composable
fun YouScreen(
    state: AppState,
    onSex: (Sex) -> Unit,
    onUnit: (WeightUnit) -> Unit,
    onSaveProfile: (String, Long?, Double?, Double?) -> Boolean,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onScanToggle: () -> Unit,
    onBind: (String) -> Unit,
    onAutoRecord: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onDiagnostics: (Boolean) -> Unit,
    onTheme: (Int) -> Unit,
    onReminder: (Int) -> Unit,
    healthAvailable: Boolean,
    healthNeedsInstall: Boolean,
    onHealthConnect: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onWipeAll: () -> Unit,
    onFetchKey: () -> Unit,
    onSaveKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile

    // The form is edited locally and only written to the store on Save. It re-seeds when the stored
    // profile changes underneath it (a save, or a backup import).
    var nameText by rememberSaveable(state.dataVersion) { mutableStateOf(profile.name) }
    var birthEpochDay by rememberSaveable(state.dataVersion) { mutableStateOf(profile.birthEpochDay) }
    var heightText by rememberSaveable(state.dataVersion) { mutableStateOf(trim(profile.heightCm)) }
    var goalText by rememberSaveable(state.dataVersion) {
        mutableStateOf(trim(goalInUnit(profile.targetKg, profile.unit)))
    }
    var pickBirthday by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 28.dp),
    ) {
        SectionHead(title = stringResource(R.string.tab_you))

        Spacer(Modifier.height(16.dp))
        IdentityCard(
            state = state,
            onPickPhoto = onPickPhoto,
            onClearPhoto = onClearPhoto,
            modifier = Modifier.enterLift(0),
        )

        Spacer(Modifier.height(14.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(1)) {
            SectionHead(title = stringResource(R.string.profile_heading))
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.sex), style = SectionHeader, color = MaterialTheme.afya.inkMuted)
            Spacer(Modifier.height(10.dp))
            // Sex is stored the moment it is tapped, so the filled pill is the only feedback it needs.
            val male = stringResource(R.string.male)
            val female = stringResource(R.string.female)
            ChoiceGroup(
                options = listOf(Sex.MALE, Sex.FEMALE),
                selected = profile.sex,
                labelOf = { if (it == Sex.FEMALE) female else male },
                onSelect = onSex,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it.take(40) },
                label = { Text(stringResource(R.string.profile_name)) },
                supportingText = { Text(stringResource(R.string.profile_name_note)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            // A date is picked, not typed: the field is a door to the calendar rather than a keyboard.
            // A readOnly text field swallows the tap before any outer clickable sees it, so the door
            // is a transparent layer laid over the whole field.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                OutlinedTextField(
                    value = if (birthEpochDay > 0L) DateFmt.birthDate(birthEpochDay) else "",
                    onValueChange = { },
                    label = { Text(stringResource(R.string.birthday)) },
                    placeholder = { Text(stringResource(R.string.birthday_unset)) },
                    supportingText = { Text(stringResource(R.string.birthday_note)) },
                    trailingIcon = {
                        Icon(AfyaIcons.Calendar, contentDescription = null, tint = afya.brand, modifier = Modifier.size(22.dp))
                    },
                    readOnly = true,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClickLabel = stringResource(R.string.birthday_pick)) { pickBirthday = true },
                )
            }
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text(stringResource(R.string.height_cm)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = { Text(stringResource(R.string.target_unit, profile.unit.label)) },
                supportingText = { Text(stringResource(R.string.target_note)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.display_unit),
                style = SectionHeader,
                color = MaterialTheme.afya.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            ChoiceGroup(
                options = WeightUnit.entries,
                selected = profile.unit,
                labelOf = { it.label },
                onSelect = onUnit,
            )

            Spacer(Modifier.height(20.dp))
            PrimaryAction(
                label = stringResource(R.string.save_profile),
                onClick = {
                    onSaveProfile(
                        nameText.trim(),
                        birthEpochDay.takeIf { it > 0L },
                        heightText.toDoubleOrNull(),
                        goalText.toDoubleOrNull()?.let { it * profile.unit.gramsPer / 1000.0 },
                    )
                },
                icon = AfyaIcons.Check,
            )
        }

        Spacer(Modifier.height(14.dp))
        HealthyRangeCard(state = state, modifier = Modifier.enterLift(2))

        Spacer(Modifier.height(20.dp))
        SectionHead(title = stringResource(R.string.settings_scale))
        Spacer(Modifier.height(12.dp))
        ScaleCard(
            state = state,
            onScanToggle = onScanToggle,
            onBind = onBind,
            modifier = Modifier.enterLift(0),
        )

        Spacer(Modifier.height(14.dp))
        AfyaCard(contentPadding = 12.dp, modifier = Modifier.fillMaxWidth().enterLift(1)) {
            ToggleRow(
                title = stringResource(R.string.settings_auto),
                hue = afya.brand,
                icon = AfyaIcons.Clock,
                checked = state.settings.autoRecord,
                onCheckedChange = onAutoRecord,
            )
            Hairline()
            ToggleRow(
                title = stringResource(R.string.settings_sound),
                hue = afya.brand,
                icon = AfyaIcons.Bell,
                checked = state.settings.sound,
                onCheckedChange = onSound,
            )
            Hairline()
            ToggleRow(
                title = stringResource(R.string.settings_haptics),
                hue = afya.brand,
                icon = AfyaIcons.Activity,
                checked = state.settings.haptics,
                onCheckedChange = onHaptics,
            )
        }

        Spacer(Modifier.height(14.dp))
        ReminderCard(
            minutes = state.settings.reminderMinutes,
            onReminder = onReminder,
            modifier = Modifier.enterLift(2),
        )

        // Shown only where Health Connect exists or can be installed; elsewhere there is nothing to offer.
        if (healthAvailable || healthNeedsInstall) {
            Spacer(Modifier.height(14.dp))
            AfyaCard(contentPadding = 12.dp, modifier = Modifier.fillMaxWidth().enterLift(2)) {
                ToggleRow(
                    title = stringResource(R.string.health_title),
                    subtitle = stringResource(
                        when {
                            !healthAvailable -> R.string.health_install
                            state.settings.healthConnect -> R.string.health_on
                            else -> R.string.health_off
                        }
                    ),
                    hue = afya.brand,
                    icon = AfyaIcons.Heart,
                    checked = state.settings.healthConnect,
                    onCheckedChange = onHealthConnect,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(2)) {
            Text(stringResource(R.string.settings_display), style = SectionHeader, color = MaterialTheme.afya.inkMuted)
            Spacer(Modifier.height(10.dp))
            val followSystem = stringResource(R.string.theme_follow)
            val lightMode = stringResource(R.string.theme_paper)
            val darkMode = stringResource(R.string.theme_carbon)
            ChoiceGroup(
                options = listOf(-1, 0, 1),
                selected = state.settings.themeMode,
                labelOf = {
                    when (it) {
                        0 -> lightMode
                        1 -> darkMode
                        else -> followSystem
                    }
                },
                onSelect = onTheme,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHead(title = stringResource(R.string.settings_data))
        Spacer(Modifier.height(12.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(0)) {
            TonalAction(
                label = stringResource(R.string.backup_json),
                onClick = onBackup,
                icon = AfyaIcons.Export,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TonalAction(
                label = stringResource(R.string.restore_json),
                onClick = onRestore,
                icon = AfyaIcons.Restore,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TonalAction(
                label = stringResource(R.string.export_csv),
                onClick = onExportCsv,
                icon = AfyaIcons.Archive,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TonalAction(
                label = stringResource(R.string.export_pdf),
                onClick = onExportPdf,
                icon = AfyaIcons.Chart,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TextAction(
                label = stringResource(R.string.start_afresh),
                onClick = onWipeAll,
                tone = MaterialTheme.afya.bandHigh,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))
        AdvancedCard(
            state = state,
            onDiagnostics = onDiagnostics,
            onFetchKey = onFetchKey,
            onSaveKey = onSaveKey,
            modifier = Modifier.enterLift(1),
        )

        Spacer(Modifier.height(14.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(2)) {
            // The one place the app introduces itself: the icon and the name, then what it is.
            AfyaLockup(
                name = stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.about_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = afya.ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.formula_note),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
            // Storyset's free licence asks for this credit wherever its illustrations appear.
            val uri = LocalUriHandler.current
            TextAction(
                label = stringResource(R.string.credit_storyset),
                onClick = { uri.openUri("https://storyset.com") },
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.version_format,
                    BuildConfig.VERSION_NAME,
                    state.readingCount,
                ),
                style = MonoBody,
                color = afya.inkMuted,
            )
        }

        Spacer(Modifier.height(26.dp))
        SignOff(modifier = Modifier.enterLift(3))
    }

    if (pickBirthday) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = birthEpochDay.takeIf { it > 0L }?.times(MILLIS_PER_DAY),
            yearRange = IntRange(1900, LocalDate.now().year),
        )
        DatePickerDialog(
            onDismissRequest = { pickBirthday = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { birthEpochDay = it / MILLIS_PER_DAY }
                        pickBirthday = false
                    },
                ) { Text(stringResource(R.string.date_use), style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { pickBirthday = false }) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                }
            },
        ) {
            DatePicker(
                state = picker,
                title = { Text(stringResource(R.string.birthday_pick)) },
                showModeToggle = false,
            )
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Who the figures are being worked out for, and what is still missing. */
@Composable
private fun IdentityCard(
    state: AppState,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    val name = profile.name.trim()
    val sexWord = stringResource(if (profile.sex == Sex.FEMALE) R.string.female else R.string.male)
    AfyaCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AfyaAvatar(
                name = name,
                fileName = profile.avatarFile,
                size = 68.dp,
                onClick = onPickPhoto,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { stringResource(R.string.profile_anon) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = afya.ink,
                )
                Text(
                    text = if (profile.complete) {
                        "$sexWord  ·  ${profile.ageYears} yrs  ·  ${trim(profile.heightCm)} cm  ·  ${profile.unit.label}"
                    } else {
                        stringResource(R.string.profile_needed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = afya.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextAction(
            label = stringResource(
                if (profile.avatarFile == null) R.string.avatar_add else R.string.avatar_change
            ),
            onClick = onPickPhoto,
            modifier = Modifier.padding(start = 0.dp),
        )
        if (profile.avatarFile != null) {
            TextAction(
                label = stringResource(R.string.avatar_remove),
                onClick = onClearPhoto,
                tone = afya.inkMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
        Spacer(Modifier.height(4.dp))
        ValueRow(
            label = stringResource(R.string.row_goal),
            value = if (profile.targetKg > 0) {
                Units.format(Units.toGrams(profile.targetKg, WeightUnit.KG), profile.unit)
            } else {
                stringResource(R.string.goal_unset)
            },
        )
    }
}

/**
 * The healthy band for this height, and where the last filed weight sits inside it. The axis is the
 * BMI band 15 to 35 so the healthy span reads as a span rather than the whole line.
 */
@Composable
private fun HealthyRangeCard(state: AppState, modifier: Modifier = Modifier) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    if (profile.heightCm <= 0) return
    val metres = profile.heightCm / 100.0
    val low = 18.5 * metres * metres
    val high = 24.9 * metres * metres
    val axisLow = 15.0 * metres * metres
    val axisHigh = 35.0 * metres * metres
    val current = state.lastSaved?.kilograms
    val span = (axisHigh - axisLow).coerceAtLeast(1.0)

    AfyaCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(image = AfyaIcons.Gauge, hue = afya.brand, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.healthy_range), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.estimates_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.bmi_bracket, massText(low, profile.unit), massText(high, profile.unit)),
                style = FigureNumber,
                color = afya.ink,
            )
            Spacer(Modifier.width(6.dp))
            Text(profile.unit.label, style = FigureUnit, color = afya.inkMuted)
        }
        Spacer(Modifier.height(14.dp))
        BandBar(
            position = (((current ?: axisLow) - axisLow) / span).toFloat().coerceIn(0f, 1f),
            tone = if (current == null) afya.inkFaint else afya.bandHealthy,
            range = (((low - axisLow) / span).toFloat()) to (((high - axisLow) / span).toFloat()),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = current?.let { "${Units.format(it * 1000, profile.unit)} · ${stringResource(R.string.row_filed)}" }
                ?: stringResource(R.string.not_filed),
            style = MaterialTheme.typography.bodySmall,
            color = afya.inkMuted,
        )
    }
}

/** The radio, what it can see, and the one written as this phone's scale. */
@Composable
private fun ScaleCard(
    state: AppState,
    onScanToggle: () -> Unit,
    onBind: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val hue = afya.brand
    AfyaCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(image = AfyaIcons.Bluetooth, hue = hue, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_bound), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.boundMac ?: stringResource(R.string.settings_none),
                    style = MonoBody,
                    color = if (state.boundMac == null) afya.inkMuted else hue,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TonalAction(
            label = stringResource(if (state.isScanning) R.string.scan_stop else R.string.scan_start),
            onClick = onScanToggle,
            icon = AfyaIcons.Scan,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (state.devices.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.boundMac == null) R.string.no_devices else R.string.scale_bound_waiting
                ),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            state.devices.forEach { device ->
                val weight = device.weight?.let { "${it.display} ${it.unit.label }" }
                    ?: stringResource(R.string.status_key_needed)
                ListRow(
                    title = device.mac,
                    subtitle = buildString {
                        if (state.settings.diagnostics) {
                            append("frame ${device.frameName} · id ${device.productId} · ")
                        }
                        append("${device.rssi} dBm · $weight")
                    },
                    hue = hue,
                    icon = AfyaIcons.Weight,
                    trailing = {
                        if (device.mac == state.boundMac) {
                            Icon(
                                AfyaIcons.Check,
                                contentDescription = stringResource(R.string.scale_is_bound),
                                tint = afya.brand,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = { onBind(device.mac) },
                )
            }
        }
    }
}

/**
 * The one daily nudge. Tapping opens a clock; the reminder only ever exists because a time was
 * picked here, and it can be switched off from the same dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    minutes: Int,
    onReminder: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    var picking by remember { mutableStateOf(false) }
    AfyaCard(contentPadding = 12.dp, modifier = modifier.fillMaxWidth()) {
        ListRow(
            title = stringResource(R.string.settings_reminder),
            subtitle = if (minutes < 0) {
                stringResource(R.string.settings_reminder_off)
            } else {
                stringResource(R.string.settings_reminder_on, ReminderTime.label(minutes))
            },
            hue = afya.brand,
            icon = AfyaIcons.Bell,
            onClick = { picking = true },
        )
    }
    if (picking) {
        val start = if (minutes >= 0) minutes else 7 * 60
        val clock = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.reminder_pick), style = MaterialTheme.typography.headlineSmall) },
            text = { TimePicker(state = clock) },
            containerColor = afya.card,
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onReminder(clock.hour * 60 + clock.minute)
                }) { Text(stringResource(R.string.reminder_set), style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                if (minutes >= 0) {
                    TextButton(onClick = {
                        picking = false
                        onReminder(-1)
                    }) {
                        Text(
                            stringResource(R.string.reminder_turn_off),
                            style = MaterialTheme.typography.labelLarge,
                            color = afya.bandHigh,
                        )
                    }
                } else {
                    TextButton(onClick = { picking = false }) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        )
    }
}

/** Diagnostics, the binding key, and the frames as they arrived. */
@Composable
private fun AdvancedCard(
    state: AppState,
    onDiagnostics: (Boolean) -> Unit,
    onFetchKey: () -> Unit,
    onSaveKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    var keyText by remember(state.boundKey, state.boundMac) { mutableStateOf(state.boundKey.orEmpty()) }

    AfyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHead(title = stringResource(R.string.settings_advanced))
        Spacer(Modifier.height(6.dp))
        NetworkLogRow(state.settings.networkLog)
        Hairline()
        ToggleRow(
            title = stringResource(R.string.diagnostics_summary),
            hue = afya.brand,
            icon = AfyaIcons.Sliders,
            checked = state.settings.diagnostics,
            onCheckedChange = onDiagnostics,
            modifier = Modifier.padding(start = 0.dp),
        )
        if (state.settings.diagnostics) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it.uppercase().take(12) },
                label = { Text(stringResource(R.string.key_label)) },
                placeholder = { Text("A1B2C3D4", style = MonoBody) },
                singleLine = true,
                textStyle = MonoBody,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalAction(
                    label = stringResource(R.string.fetch_key),
                    onClick = onFetchKey,
                    icon = AfyaIcons.Lock,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                TextAction(label = stringResource(R.string.save_key), onClick = { onSaveKey(keyText) })
            }
            if (state.rawFrames.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.raw_frames),
                    style = SectionHeader,
                    color = afya.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.rawFrames.joinToString("\n"),
                    style = MonoBody,
                    color = afya.inkMuted,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * Every request that has left the phone, so "one network call, ever" is something the owner can check
 * rather than take on trust. Collapsed to a count until opened.
 */
@Composable
private fun NetworkLogRow(log: List<NetworkCall>) {
    val afya = MaterialTheme.afya
    var open by rememberSaveable { mutableStateOf(false) }
    ListRow(
        title = stringResource(R.string.network_log),
        subtitle = if (log.isEmpty()) {
            stringResource(R.string.network_log_empty)
        } else {
            pluralStringResource(R.plurals.network_log_count, log.size, log.size, DateFmt.dayLabel(log.first().timestampMs))
        },
        hue = afya.brand,
        icon = AfyaIcons.Lock,
        onClick = if (log.isEmpty()) null else ({ open = !open }),
    )
    if (open) {
        log.forEach { call ->
            Text(
                text = "${DateFmt.full(call.timestampMs)}  ${call.host}\n${call.mac} · ${call.outcome}",
                style = MonoBody,
                color = afya.inkMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * The closing of the page. It carries no card because it is not a setting: the purpose the whole
 * screen serves, then the person who wrote it. The heart is the only place the glyph appears.
 */
@Composable
private fun SignOff(modifier: Modifier = Modifier) {
    val afya = MaterialTheme.afya
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_purpose),
            style = MaterialTheme.typography.bodyLarge,
            color = afya.ink,
        )
        Spacer(Modifier.height(18.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AfyaIcons.Heart,
                contentDescription = null,
                tint = afya.brand,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.signature_by),
                    style = MaterialTheme.typography.titleSmall,
                    color = afya.ink,
                )
                Text(
                    text = stringResource(R.string.signature_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        }
    }
}

/** The profile form shows plain numbers; a whole height or goal should not read "178.0". */
private fun trim(value: Double): String =
    if (value > 0 && value % 1.0 == 0.0) value.toInt().toString()
    else if (value > 0) "%.1f".format(value)
    else ""

/** The goal is written and read in the unit the person displays, never a hidden kilogram figure. */
private fun goalInUnit(kg: Double, unit: WeightUnit): Double = if (kg > 0) kg * 1000 / unit.gramsPer else 0.0

private fun massText(kg: Double, unit: WeightUnit): String = when (unit) {
    WeightUnit.LB, WeightUnit.ST_LB -> "%.1f".format(kg * 1000 / WeightUnit.LB.gramsPer)
    WeightUnit.JIN -> "%.1f".format(kg * 2)
    else -> "%.1f".format(kg)
}
