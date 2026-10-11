package com.maximillianurio.afya.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maximillianurio.afya.R
import com.maximillianurio.afya.Reminders
import com.maximillianurio.afya.app.AfyaViewModel
import com.maximillianurio.afya.core.ReportRow
import com.maximillianurio.afya.ui.components.AfyaDialog
import com.maximillianurio.afya.ui.components.MetricHistorySheet
import com.maximillianurio.afya.ui.components.PassphraseDialog
import com.maximillianurio.afya.data.BackupCrypto
import com.maximillianurio.afya.data.HealthShare
import androidx.health.connect.client.PermissionController
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.screens.HomeScreen
import com.maximillianurio.afya.ui.screens.MeasureScreen
import com.maximillianurio.afya.ui.screens.PlanScreen
import com.maximillianurio.afya.ui.screens.RecordsScreen
import com.maximillianurio.afya.ui.screens.YouScreen
import com.maximillianurio.afya.ui.theme.afya
import com.maximillianurio.afya.ui.theme.reducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val index: Int, @param:StringRes val label: Int, val icon: ImageVector) {
    Home(0, R.string.tab_home, AfyaIcons.Home),
    Measure(1, R.string.tab_measure, AfyaIcons.Measure),
    Plan(2, R.string.tab_plan, AfyaIcons.Plan),
    Records(3, R.string.tab_history, AfyaIcons.Records),
    You(4, R.string.tab_you, AfyaIcons.You),
}

/**
 * The five destinations, the system prompts the scanner needs, and the local backup dialogs.
 * [weighInRequests] counts launcher-shortcut and Quick Settings requests for the platform; each new
 * one switches to Measure.
 */
@Composable
fun AfyaApp(vm: AfyaViewModel, weighInRequests: Int = 0) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val afya = MaterialTheme.afya
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(Tab.Home.index) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Android stops showing the prompt after the second refusal; from then on only Settings can grant it.
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(weighInRequests) {
        if (weighInRequests > 0) tab = Tab.Measure.index
    }

    // Back walks to Home before it leaves the app, the way every bottom-bar app on the phone does.
    BackHandler(enabled = tab != Tab.Home.index) { tab = Tab.Home.index }
    // The figure whose history is open in the sheet, over whichever tab it was tapped on.
    var sheetRow by remember { mutableStateOf<ReportRow?>(null) }

    val bluetoothOff = stringResource(R.string.status_bt_off)
    val say: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    val openMetric = { row: ReportRow -> sheetRow = row }

    val writeFile: (Uri, suspend () -> ByteArray) -> Unit = { uri, body ->
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val payload = body()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                    payload
                }
                say(context.getString(R.string.file_written))
            } catch (e: Exception) {
                say(context.getString(R.string.file_failed))
            }
        }
    }

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onBluetoothResult(vm.adapterEnabled) }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        val activity = context.findActivity()
        permissionBlocked = !granted && activity != null && results.any { (permission, ok) ->
            !ok && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        vm.onPermissionsResult(granted)
        // Same chain as the old activity: a granted prompt still has to get the radio on.
        if (granted && !vm.adapterEnabled) enableBluetooth.launch(AfyaViewModel.enableBluetoothIntent)
    }

    val requestScan: () -> Unit = {
        when (vm.requestScanning()) {
            AfyaViewModel.ScanRequest.NeedsPermissions ->
                if (permissionBlocked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                } else {
                    requestPermissions.launch(vm.scanPermissions())
                }

            AfyaViewModel.ScanRequest.NeedsBluetooth ->
                enableBluetooth.launch(AfyaViewModel.enableBluetoothIntent)

            AfyaViewModel.ScanRequest.Scanning -> Unit
        }
    }

    // The reminder is only switched on once Android has agreed to show it.
    var pendingReminder by rememberSaveable { mutableIntStateOf(-1) }
    val reminderDenied = stringResource(R.string.reminder_denied)
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingReminder >= 0) vm.setReminder(pendingReminder) else say(reminderDenied)
        pendingReminder = -1
    }

    val requestHealth = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> vm.setHealthConnect(granted.containsAll(HealthShare.PERMISSIONS)) }

    // Held between the passphrase dialog and the file picker; empty means a plain backup.
    var backupPassphrase by remember { mutableStateOf("") }
    var askBackupPassphrase by remember { mutableStateOf(false) }
    val backup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val passphrase = backupPassphrase
        backupPassphrase = ""
        if (uri != null) {
            writeFile(uri) {
                val json = vm.exportJson()
                (if (passphrase.isEmpty()) json else BackupCrypto.seal(json, passphrase)).toByteArray()
            }
        }
    }

    val csv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) writeFile(uri) { vm.exportCsv().toByteArray() } }

    val pdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) writeFile(uri) { vm.exportPdf() } }

    /** The system photo picker: the person chooses from their own library, no storage permission asked. */
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.setAvatarFrom(uri) }

    val importText: (String) -> Unit = { text ->
        try {
            val summary = vm.importJson(text)
            say(context.getString(R.string.restore_done, summary.readings, summary.skipped))
        } catch (e: Exception) {
            say(context.getString(R.string.restore_not_afya))
        }
    }

    // An encrypted backup waiting for its passphrase, and what went wrong with the last try.
    var sealedBackup by remember { mutableStateOf<String?>(null) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {
                null
            }
            when {
                text.isNullOrBlank() -> say(context.getString(R.string.restore_unreadable))
                BackupCrypto.isSealed(text) -> {
                    passphraseError = null
                    sealedBackup = text
                }
                else -> importText(text)
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.notices.collect { notice ->
            val result = snackbarHostState.showSnackbar(notice.message, notice.actionLabel)
            if (result == SnackbarResult.ActionPerformed) notice.onAction?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        if (vm.adapterEnabled) requestScan() else say(bluetoothOff)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Back from Settings with the permission switched on.
                    if (vm.hasScanPermissions()) permissionBlocked = false
                    vm.onResumed()
                }
                Lifecycle.Event.ON_PAUSE -> vm.onPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = afya.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = afya.card) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.index,
                        onClick = { tab = item.index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = afya.brandDeep,
                            selectedTextColor = afya.brandDeep,
                            unselectedIconColor = afya.inkFaint,
                            unselectedTextColor = afya.inkMuted,
                            indicatorColor = afya.brandWash,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // Fade-through between tabs: the old page leaves quickly, the new one arrives just after it,
        // so a switch reads as one move rather than a cut. Removed animations make it a plain cut.
        val reduced = reducedMotion()
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(210, delayMillis = 70)) + scaleIn(tween(210, delayMillis = 70), initialScale = 0.985f)) togetherWith
                        fadeOut(tween(90))
                }
            },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.Home.index -> HomeScreen(
                    state = state,
                    lastEntry = state.lastSaved,
                    previousEntry = state.lastSaved?.let { vm.previousTo(it.timestampMs) },
                    trendKg = remember(state.dataVersion) { vm.trendKg() },
                    metricsOf = vm::metricsOf,
                    recent = { vm.allReadings().take(6) },
                    onSelectMetric = openMetric,
                    onEntryClick = { tab = Tab.Records.index },
                    onMeasure = { tab = Tab.Measure.index },
                    onConnectionClick = { tab = Tab.You.index },
                    goToYou = { tab = Tab.You.index },
                    onSeeAll = { tab = Tab.Records.index },
                )

                Tab.Measure.index -> MeasureScreen(
                    state = state,
                    previousTo = vm::previousTo,
                    recent = { vm.readingsSince(System.currentTimeMillis() - 30 * 86_400_000L) },
                    metricsOf = vm::metricsOf,
                    onRecord = { vm.recordNow() },
                    onConfirmMine = vm::confirmMine,
                    onNotMine = vm::notMine,
                    onDeleteReading = vm::deleteReading,
                    onSelectMetric = openMetric,
                    goToYou = { tab = Tab.You.index },
                    onOpenRecords = { tab = Tab.Records.index },
                    onScan = requestScan,
                    onRetryKey = { vm.retryKeyFetch() },
                    trendKg = remember(state.dataVersion) { vm.trendKg() },
                    permissionBlocked = permissionBlocked,
                )

                Tab.Plan.index -> PlanScreen(
                    state = state,
                    metricsOf = vm::metricsOf,
                    allReadings = vm::allReadings,
                    drinksToday = vm::drinksToday,
                    drinkTotal = vm::drinkTotalToday,
                    drinkKindOf = vm::drinkKindOf,
                    onLogDrink = { kind, millilitres -> vm.logDrink(kind, millilitres) },
                    onDeleteDrink = vm::deleteDrink,
                    mealsToday = { vm.mealsToday() },
                    mealTotalKcal = { vm.mealTotalKcalToday() },
                    onLogMeal = { food, amount, kcal -> vm.logMeal(food, amount, kcal) },
                    onDeleteMeal = vm::deleteMeal,
                    onSaveIntent = vm::saveIntent,
                    onActivity = vm::setActivity,
                    onHeat = vm::setHeat,
                    onWaterGoal = vm::setWaterGoalMl,
                    onCalorieGoal = vm::setCalorieGoalKcal,
                    goToYou = { tab = Tab.You.index },
                )

                Tab.Records.index -> RecordsScreen(
                    state = state,
                    allReadings = vm::allReadings,
                    metricsOf = vm::metricsOf,
                    onSelectMetric = openMetric,
                    measurements = vm::measurementsBySite,
                    onSaveMeasurement = vm::saveMeasurement,
                    onDeleteMeasurement = vm::deleteMeasurement,
                    onDeleteReading = vm::deleteReading,
                    onLengthUnits = vm::setLengthInches,
                )

                Tab.You.index -> YouScreen(
                    state = state,
                    onSex = vm::setSex,
                    onUnit = vm::setUnit,
                    onSaveProfile = vm::saveProfile,
                    onPickPhoto = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearPhoto = vm::clearAvatar,
                    onScanToggle = { if (state.isScanning) vm.stopScanning() else requestScan() },
                    onBind = vm::bindTo,
                    onAutoRecord = vm::setAutoRecord,
                    onSound = vm::setSound,
                    onHaptics = vm::setHaptics,
                    onDiagnostics = vm::setDiagnostics,
                    onTheme = vm::setThemeMode,
                    healthAvailable = vm.healthAvailable,
                    healthNeedsInstall = vm.healthNeedsInstall,
                    onHealthConnect = { on ->
                        when {
                            !on -> vm.setHealthConnect(false)
                            vm.healthAvailable -> requestHealth.launch(HealthShare.PERMISSIONS)
                            else -> context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_PACKAGE"))
                                    .setPackage("com.android.vending")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    onReminder = { minutes ->
                        if (minutes >= 0 && !Reminders.canNotify(context)) {
                            pendingReminder = minutes
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setReminder(minutes)
                        }
                    },
                    onBackup = {
                        if (state.readingCount == 0) say(context.getString(R.string.nothing_recorded))
                        else askBackupPassphrase = true
                    },
                    onRestore = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExportCsv = {
                        if (state.readingCount == 0) say(context.getString(R.string.nothing_recorded))
                        else csv.launch("afya-readings-${System.currentTimeMillis()}.csv")
                    },
                    onExportPdf = {
                        if (state.readingCount == 0) say(context.getString(R.string.nothing_recorded))
                        else pdf.launch("afya-record-${System.currentTimeMillis()}.pdf")
                    },
                    onWipeAll = { confirmDelete = true },
                    onFetchKey = {
                        val mac = state.boundMac
                        if (mac == null) say(context.getString(R.string.bind_first)) else vm.fetchKey(mac)
                    },
                    onSaveKey = { key ->
                        val mac = state.boundMac
                        val value = key.trim()
                        when {
                            mac == null -> say(context.getString(R.string.bind_first))
                            !vm.saveKey(mac, value) -> say(context.getString(R.string.key_format))
                        }
                    },
                )
            }
        }
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = stringResource(R.string.backup_pass_title),
            message = stringResource(R.string.backup_pass_body),
            fieldLabel = stringResource(R.string.backup_pass_field),
            confirmLabel = stringResource(R.string.backup_encrypted),
            plainLabel = stringResource(R.string.backup_plain),
            dismissLabel = stringResource(R.string.cancel),
            optional = true,
            onConfirm = { passphrase ->
                askBackupPassphrase = false
                backupPassphrase = passphrase
                backup.launch("afya-backup-${System.currentTimeMillis()}.json")
            },
            onDismiss = { askBackupPassphrase = false },
        )
    }

    sealedBackup?.let { text ->
        PassphraseDialog(
            title = stringResource(R.string.restore_pass_title),
            message = stringResource(R.string.restore_pass_body),
            fieldLabel = stringResource(R.string.backup_pass_field),
            confirmLabel = stringResource(R.string.restore_open),
            dismissLabel = stringResource(R.string.cancel),
            error = passphraseError,
            onConfirm = { passphrase ->
                scope.launch {
                    val plain = withContext(Dispatchers.Default) {
                        runCatching { BackupCrypto.open(text, passphrase) }
                    }
                    plain.onSuccess {
                        sealedBackup = null
                        importText(it)
                    }.onFailure {
                        passphraseError = context.getString(
                            if (it is BackupCrypto.WrongPassphrase) R.string.restore_wrong_pass
                            else R.string.restore_not_afya
                        )
                    }
                }
            },
            onDismiss = { sealedBackup = null },
        )
    }

    sheetRow?.let { row ->
        val history = remember(row.name, state.dataVersion, state.profile.unit) { vm.metricHistory(row.name) }
        MetricHistorySheet(
            row = row,
            history = history.points,
            leftOut = history.leftOut,
            sex = state.profile.sex,
            onDismiss = { sheetRow = null },
        )
    }

    if (confirmDelete) {
        AfyaDialog(
            title = stringResource(R.string.wipe_title),
            message = stringResource(R.string.wipe_message),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                val cleared = vm.clearEverything()
                say(
                    if (cleared.total == 0) context.getString(R.string.wipe_nothing)
                    else context.getString(
                        R.string.wipe_done,
                        cleared.readings,
                        cleared.measurements,
                        cleared.drinks,
                        cleared.meals,
                    )
                )
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The activity behind a Compose context, for the calls that still need one. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val HEALTH_PACKAGE = "com.google.android.apps.healthdata"
