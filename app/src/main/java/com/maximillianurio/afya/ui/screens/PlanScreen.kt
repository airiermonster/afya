package com.maximillianurio.afya.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R
import com.maximillianurio.afya.app.AppState
import com.maximillianurio.afya.core.ActivityLevel
import com.maximillianurio.afya.core.Aim
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.BodyMetrics
import com.maximillianurio.afya.core.CheckIn
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.DrinkKind
import com.maximillianurio.afya.core.Heat
import com.maximillianurio.afya.core.IntentProfile
import com.maximillianurio.afya.core.Motivation
import com.maximillianurio.afya.core.Obstacle
import com.maximillianurio.afya.core.Pace
import com.maximillianurio.afya.core.Targets
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.deltaParts
import com.maximillianurio.afya.data.ReadingsDb
import com.maximillianurio.afya.ui.components.AfyaCard
import com.maximillianurio.afya.ui.components.ChoiceGroup
import com.maximillianurio.afya.ui.components.ChoicePill
import com.maximillianurio.afya.ui.components.EmptyState
import com.maximillianurio.afya.ui.components.Hairline
import com.maximillianurio.afya.ui.components.IconTile
import com.maximillianurio.afya.ui.components.Illustration
import com.maximillianurio.afya.ui.components.ProgressRing
import com.maximillianurio.afya.ui.components.RingRow
import com.maximillianurio.afya.ui.components.RingStat
import com.maximillianurio.afya.ui.components.Scene
import com.maximillianurio.afya.ui.components.SectionHead
import com.maximillianurio.afya.ui.components.TextAction
import com.maximillianurio.afya.ui.components.TonalAction
import com.maximillianurio.afya.ui.components.TrendArea
import com.maximillianurio.afya.ui.components.ValueRow
import com.maximillianurio.afya.ui.components.enterLift
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.FigureNumber
import com.maximillianurio.afya.ui.theme.FigureUnit
import com.maximillianurio.afya.ui.theme.SectionHeader
import com.maximillianurio.afya.ui.theme.TileValue
import com.maximillianurio.afya.ui.theme.afya
import kotlin.math.abs

/**
 * The plan page: what the body is asking for today, worked from the last filed weigh-in. Movement and
 * climate are the two knobs that move a goal without stepping on the scale, and either goal can be
 * written over by hand — the tally is then measured against the written figure, not the guideline.
 * The intent interview runs once, on the first visit, and after that the plan leads with what was said.
 */
@Composable
fun PlanScreen(
    state: AppState,
    metricsOf: (ReadingsDb.Entry) -> BodyMetrics?,
    allReadings: () -> List<ReadingsDb.Entry>,
    drinksToday: () -> List<ReadingsDb.Drink>,
    drinkTotal: () -> Int,
    drinkKindOf: (String) -> DrinkKind,
    onLogDrink: (DrinkKind, Int) -> Unit,
    onDeleteDrink: (Long) -> Unit,
    mealsToday: () -> List<ReadingsDb.Meal>,
    mealTotalKcal: () -> Int?,
    onLogMeal: (String, String, Int?) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onSaveIntent: (IntentProfile) -> Unit,
    onActivity: (ActivityLevel) -> Unit,
    onHeat: (Heat) -> Unit,
    onWaterGoal: (Int?) -> Unit,
    onCalorieGoal: (Int?) -> Unit,
    goToYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filed = state.lastSaved
    val metrics = filed?.let { metricsOf(it) }
    var editing by remember { mutableStateOf<GoalEdit?>(null) }
    var logging by remember { mutableStateOf(false) }
    // -1 means the interview is closed. It only opens when asked for, from the intent card: a plan
    // that greets a first visit with a five-step dialog stands between the person and their numbers.
    var stage by rememberSaveable { mutableIntStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 28.dp),
    ) {
        SectionHead(title = stringResource(R.string.plan_heading))

        if (filed == null || metrics == null) {
            Spacer(Modifier.height(16.dp))
            AfyaCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    title = stringResource(R.string.plan_no_data),
                    body = stringResource(R.string.estimates_note),
                    scene = Scene.Heading,
                    action = {
                        TextAction(label = stringResource(R.string.plan_set_goal), onClick = goToYou)
                    },
                )
            }
        } else {
            PlanContent(
                state = state,
                filed = filed,
                metrics = metrics,
                allReadings = allReadings,
                drinksToday = drinksToday,
                drinkTotal = drinkTotal,
                drinkKindOf = drinkKindOf,
                onLogDrink = onLogDrink,
                onDeleteDrink = onDeleteDrink,
                onEditIntent = { stage = 0 },
                mealsToday = mealsToday,
                mealTotalKcal = mealTotalKcal,
                onLogMeal = { logging = true },
                onDeleteMeal = onDeleteMeal,
                onActivity = onActivity,
                onHeat = onHeat,
                onWaterGoal = onWaterGoal,
                onCalorieGoal = onCalorieGoal,
                onWriteGoal = { editing = it },
            )
        }
    }

    if (stage in 0..4) {
        IntentInterview(
            start = state.profile.intent,
            stage = stage,
            onStage = { stage = it },
            onDismiss = { stage = -1 },
            onSave = {
                onSaveIntent(it)
                stage = -1
            },
        )
    }

    if (logging) {
        MealDialog(
            onSave = { food, amount, kcal ->
                onLogMeal(food, amount, kcal)
                logging = false
            },
            onDismiss = { logging = false },
        )
    }

    val edit = editing
    if (edit != null) {
        GoalDialog(
            field = edit.field,
            current = edit.current,
            onSave = { value ->
                if (edit.field == GoalField.WATER) onWaterGoal(value) else onCalorieGoal(value)
            },
            onDismiss = { editing = null },
        )
    }
}

/** The two halves of the plan: what to log today, and where it is heading. */
private enum class PlanSection(@param:StringRes val label: Int) {
    TODAY(R.string.plan_today),
    GOAL(R.string.plan_goal),
}

/** Which goal the dialog is writing, and the figure it opens on. */
private enum class GoalField { WATER, CALORIE }

private data class GoalEdit(val field: GoalField, val current: Int)

@Composable
private fun PlanContent(
    state: AppState,
    filed: ReadingsDb.Entry,
    metrics: BodyMetrics,
    allReadings: () -> List<ReadingsDb.Entry>,
    drinksToday: () -> List<ReadingsDb.Drink>,
    drinkTotal: () -> Int,
    drinkKindOf: (String) -> DrinkKind,
    onLogDrink: (DrinkKind, Int) -> Unit,
    onDeleteDrink: (Long) -> Unit,
    onEditIntent: () -> Unit,
    mealsToday: () -> List<ReadingsDb.Meal>,
    mealTotalKcal: () -> Int?,
    onLogMeal: () -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onActivity: (ActivityLevel) -> Unit,
    onHeat: (Heat) -> Unit,
    onWaterGoal: (Int?) -> Unit,
    onCalorieGoal: (Int?) -> Unit,
    onWriteGoal: (GoalEdit) -> Unit,
) {
    val afya = MaterialTheme.afya
    val profile = state.profile
    val water = Targets.water(metrics.leanMassKg, profile.activity, profile.heat)
    val waterGoal = state.settings.waterGoalMl ?: water.millilitres
    val energy = Targets.energy(
        metrics.bmrKcal,
        profile.activity,
        filed.kilograms,
        profile.targetKg,
        profile.sex,
        profile.intent,
    )
    val calorieGoal = state.settings.calorieGoalKcal ?: energy.targetKcal

    var section by rememberSaveable { mutableStateOf(PlanSection.TODAY) }

    // Two pages in one tab: what to log today, and where it is all heading.
    Spacer(Modifier.height(14.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PlanSection.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = section == option,
                onClick = { section = option },
                shape = SegmentedButtonDefaults.itemShape(index, PlanSection.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = afya.brandWash,
                    activeContentColor = afya.brandDeep,
                    inactiveContainerColor = afya.card,
                    inactiveContentColor = afya.inkMuted,
                    activeBorderColor = afya.hairline,
                    inactiveBorderColor = afya.hairline,
                ),
                label = { Text(stringResource(option.label)) },
            )
        }
    }

    if (section == PlanSection.TODAY) {
        Spacer(Modifier.height(16.dp))
        WaterCard(
            overridden = state.settings.waterGoalMl != null,
            water = water,
            goal = waterGoal,
            logged = drinkTotal(),
            onWrite = { onWriteGoal(GoalEdit(GoalField.WATER, waterGoal)) },
            onFollow = { onWaterGoal(null) },
            modifier = Modifier.enterLift(0),
        )

        Spacer(Modifier.height(14.dp))
        DrinkCard(
            rows = drinksToday(),
            drinkKindOf = drinkKindOf,
            logged = drinkTotal(),
            onLog = onLogDrink,
            onDelete = onDeleteDrink,
            modifier = Modifier.enterLift(1),
        )

        Spacer(Modifier.height(14.dp))
        EnergyCard(
            overridden = state.settings.calorieGoalKcal != null,
            energy = energy,
            goal = calorieGoal,
            onWrite = { onWriteGoal(GoalEdit(GoalField.CALORIE, calorieGoal)) },
            onFollow = { onCalorieGoal(null) },
            modifier = Modifier.enterLift(2),
        )

        Spacer(Modifier.height(14.dp))
        MealCard(
            rows = mealsToday(),
            loggedKcal = mealTotalKcal(),
            goalKcal = calorieGoal,
            onAdd = onLogMeal,
            onDelete = onDeleteMeal,
            modifier = Modifier.enterLift(3),
        )
    } else {
        // The question the scale is stood on for comes first.
        Spacer(Modifier.height(16.dp))
        ProjectionCard(
            state = state,
            filed = filed,
            allReadings = allReadings,
            modifier = Modifier.enterLift(0),
        )

        Spacer(Modifier.height(14.dp))
        IntentCard(intent = profile.intent, onEdit = onEditIntent, modifier = Modifier.enterLift(1))

        Spacer(Modifier.height(14.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(2)) {
            Knob(
                label = stringResource(R.string.plan_activity),
                options = ActivityLevel.entries,
                selected = profile.activity,
                labelOf = { it.label },
                onSelect = onActivity,
                caption = profile.activity.note,
            )
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Knob(
                label = stringResource(R.string.plan_heat),
                options = Heat.entries,
                selected = profile.heat,
                labelOf = { it.label },
                onSelect = onHeat,
            )
        }

        Spacer(Modifier.height(14.dp))
        AfyaCard(modifier = Modifier.fillMaxWidth().enterLift(3)) {
            FigureHeader(
                title = stringResource(R.string.plan_body_type),
                qualifier = stringResource(R.string.plan_body_type_note),
                icon = AfyaIcons.Body,
                hue = afya.hue("bmi"),
                figure = Targets.bodyType(metrics.bmi, metrics.fatPercent, profile.sex).label,
                unit = "",
            )
        }
    }

    Text(
        text = stringResource(R.string.plan_note),
        style = MaterialTheme.typography.bodySmall,
        color = afya.inkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    )
}

/** A knob the plan reads from: the whole choice set laid out as pills, nothing hidden in a dropdown. */
@Composable
private fun <T> Knob(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    caption: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = SectionHeader, color = MaterialTheme.afya.inkMuted)
        Spacer(Modifier.height(10.dp))
        ChoiceGroup(options = options, selected = selected, labelOf = labelOf, onSelect = onSelect)
        if (caption != null) {
            Spacer(Modifier.height(10.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.afya.inkMuted)
        }
    }
}

/** The card opening line: glyph, what it is, how it was derived, and the figure itself. */
@Composable
private fun FigureHeader(
    title: String,
    qualifier: String,
    icon: ImageVector,
    hue: Color,
    figure: String,
    unit: String,
) {
    val afya = MaterialTheme.afya
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(image = icon, hue = hue, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = afya.ink)
            Text(qualifier, style = MaterialTheme.typography.bodySmall, color = afya.inkMuted)
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = figure,
            style = if (unit.isEmpty()) MaterialTheme.typography.titleMedium else FigureNumber,
            color = afya.ink,
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(unit, style = FigureUnit, color = afya.inkMuted)
        }
    }
}

@Composable
private fun WaterCard(
    overridden: Boolean,
    water: Targets.Water,
    goal: Int,
    logged: Int,
    onWrite: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue("water")
    // A hand-written goal can be 0, which would make the tally fraction meaningless rather than empty.
    val fraction = if (goal <= 0) 0f else (logged.toFloat() / goal).coerceIn(0f, 1f)
    val over = goal > 0 && logged >= goal

    AfyaCard(modifier = modifier.fillMaxWidth()) {
        FigureHeader(
            title = stringResource(R.string.plan_water),
            qualifier = stringResource(
                if (overridden) R.string.plan_water_overridden else R.string.plan_water_recommended
            ),
            icon = AfyaIcons.Droplet,
            hue = hue,
            figure = "$goal",
            unit = "ml",
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = fraction,
                hue = if (over) afya.bandHealthy else hue,
                ringSize = 112.dp,
                stroke = 12.dp,
                content = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$logged", style = TileValue, color = afya.ink)
                        Spacer(Modifier.width(3.dp))
                        Text("ml", style = FigureUnit, color = afya.inkMuted)
                    }
                },
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                ValueRow(stringResource(R.string.plan_water_base), "${water.baseMl} ml")
                Hairline()
                ValueRow(
                    stringResource(R.string.plan_water_activity),
                    if (water.activityMl == 0) "—" else "+${water.activityMl} ml",
                )
                Hairline()
                ValueRow(
                    stringResource(R.string.plan_water_heat),
                    if (water.heatMl == 0) "—" else "+${water.heatMl} ml",
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ValueRow(
            label = stringResource(R.string.plan_water_logged),
            value = "$logged ml",
        )
        ValueRow(
            label = stringResource(if (over) R.string.plan_water_over else R.string.plan_water_left),
            value = "${abs(goal - logged)} ml",
        )
        GoalActions(
            overridden = overridden,
            onWrite = onWrite,
            onFollow = onFollow,
        )
    }
}

@Composable
private fun GoalActions(overridden: Boolean, onWrite: () -> Unit, onFollow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextAction(label = stringResource(R.string.plan_write_own), onClick = onWrite)
        if (overridden) {
            TextAction(
                label = stringResource(R.string.plan_follow_recommendation),
                onClick = onFollow,
                tone = MaterialTheme.afya.inkMuted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkCard(
    rows: List<ReadingsDb.Drink>,
    drinkKindOf: (String) -> DrinkKind,
    logged: Int,
    onLog: (DrinkKind, Int) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue("water")
    AfyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHead(
            title = stringResource(R.string.plan_drinks),
            action = {
                Text(
                    text = pluralStringResource(R.plurals.schedule_rows, rows.size, rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.inkMuted,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrinkKind.entries.forEach { kind ->
                ChoicePill(
                    label = "${kind.label} ${kind.defaultMl}",
                    selected = false,
                    onClick = { onLog(kind, kind.defaultMl) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (rows.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Illustration(Scene.Glass, width = 72.dp, height = 48.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.plan_drinks_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        } else {
            Column {
                rows.forEachIndexed { index, drink ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(hue.copy(alpha = afya.tileAlpha)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AfyaIcons.Glass,
                                contentDescription = null,
                                tint = hue,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Text(
                            text = "${drinkKindOf(drink.kind).label}  ·  ${drink.millilitres} ml",
                            style = MaterialTheme.typography.bodyMedium,
                            color = afya.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = DateFmt.clock(drink.timestampMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = afya.inkMuted,
                        )
                        // Same rule as meals: only the labelled bin removes a row.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = stringResource(R.string.plan_drink_remove)) {
                                    onDelete(drink.id)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AfyaIcons.Trash,
                                contentDescription = null,
                                tint = afya.inkFaint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (index < rows.lastIndex) Hairline()
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$logged ml",
                    style = MaterialTheme.typography.titleMedium,
                    color = hue,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun EnergyCard(
    overridden: Boolean,
    energy: Targets.Energy,
    goal: Int,
    onWrite: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue("bmr")
    // Each ring is that macro's share of the day, so the three read as one distribution.
    val carbsKcal = energy.carbsGrams * 4
    val proteinKcal = energy.proteinGrams * 4
    val fatKcal = energy.fatGrams * 9
    val total = (carbsKcal + proteinKcal + fatKcal).coerceAtLeast(1.0)

    AfyaCard(modifier = modifier.fillMaxWidth()) {
        FigureHeader(
            title = stringResource(R.string.plan_energy),
            qualifier = stringResource(directionLabel(energy.direction)),
            icon = AfyaIcons.Flame,
            hue = hue,
            figure = "$goal",
            unit = "kcal",
        )
        Spacer(Modifier.height(18.dp))
        RingRow(
            stats = listOf(
                RingStat(
                    label = stringResource(R.string.plan_carbs),
                    valueText = "%.0f".format(energy.carbsGrams),
                    unit = "g",
                    fraction = (carbsKcal / total).toFloat(),
                    hueName = "bmr",
                ),
                RingStat(
                    label = stringResource(R.string.plan_protein),
                    valueText = "%.0f".format(energy.proteinGrams),
                    unit = "g",
                    fraction = (proteinKcal / total).toFloat(),
                    hueName = "protein",
                ),
                RingStat(
                    label = stringResource(R.string.plan_fat),
                    valueText = "%.0f".format(energy.fatGrams),
                    unit = "g",
                    fraction = (fatKcal / total).toFloat(),
                    hueName = "fat",
                ),
            ),
            ringSize = 84.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.plan_meals),
            style = SectionHeader,
            color = afya.inkMuted,
        )
        Spacer(Modifier.height(10.dp))
        MealBoxes(energy.mealsKcal)
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(4.dp))
        ValueRow(stringResource(R.string.plan_maintenance), "${energy.maintenanceKcal} kcal")
        GoalActions(overridden = overridden, onWrite = onWrite, onFollow = onFollow)
    }
}

/** The day's energy split, four wells to a line. */
@Composable
private fun MealBoxes(meals: List<Pair<String, Int>>) {
    val afya = MaterialTheme.afya
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        meals.forEach { (label, kcal) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(afya.cardTint)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = SectionHeader,
                    color = afya.inkMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text("$kcal", style = MaterialTheme.typography.titleMedium, color = afya.ink)
            }
        }
    }
}

/** The goal date, read off the rate the readings themselves show, with the pencil line on the grid. */
@Composable
private fun ProjectionCard(
    state: AppState,
    filed: ReadingsDb.Entry,
    allReadings: () -> List<ReadingsDb.Entry>,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val unit = state.profile.unit
    val entries = remember(allReadings, state.dataVersion) {
        allReadings()
            .filter { BodyComposition.bodyPlausible(it.grams) }
            .sortedBy { it.timestampMs }
    }
    val projection = Targets.project(
        entries.map { Targets.ReadingsDbEntry(it.timestampMs, it.grams) },
        filed.kilograms,
        state.profile.targetKg,
        System.currentTimeMillis(),
    )

    AfyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHead(title = stringResource(R.string.plan_projection))
        if (projection == null) {
            Spacer(Modifier.height(12.dp))
            val span = entries.lastOrNull()?.let { it.timestampMs - entries.first().timestampMs } ?: 0L
            Illustration(Scene.Heading, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    when {
                        state.profile.targetKg <= 0 -> R.string.plan_projection_no_goal
                        span < 86_400_000L -> R.string.plan_projection_none
                        else -> R.string.plan_projection_away
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = afya.inkMuted,
            )
            return@AfyaCard
        }
        val goalDisplay = state.profile.targetKg * 1000 / unit.gramsPer
        Spacer(Modifier.height(14.dp))
        TrendArea(
            points = entries.map { it.timestampMs to it.grams / unit.gramsPer },
            hue = afya.hue("weight"),
            goal = goalDisplay,
            projection = projection.etaDays to goalDisplay,
            emptyLabel = stringResource(R.string.ledger_low_data),
        )
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(4.dp))
        ValueRow(
            label = stringResource(R.string.plan_projection_left, Units.format(projection.remainingKg * 1000.0, unit)),
            value = stringResource(
                R.string.plan_projection_rate_short,
                deltaParts(projection.weeklyRateKg, unit).second,
            ),
        )
        ValueRow(
            label = stringResource(R.string.plan_projection_weeks),
            value = stringResource(R.string.plan_projection_weeks_value, projection.weeks),
        )
        ValueRow(
            label = stringResource(R.string.plan_projection_date),
            value = DateFmt.full(projection.etaDays).substringBefore(' '),
        )
        if (!projection.onTrack) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(afya.bandWarn.copy(alpha = afya.tileAlpha))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    AfyaIcons.Alert,
                    contentDescription = null,
                    tint = afya.bandWarn,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.plan_projection_fast),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.ink,
                )
            }
        }
    }
}

@Composable
private fun GoalDialog(
    field: GoalField,
    current: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(field) { mutableStateOf(current.toString()) }
    val afya = MaterialTheme.afya
    val label = stringResource(if (field == GoalField.WATER) R.string.plan_water else R.string.plan_energy)
    val unit = if (field == GoalField.WATER) "ml" else "kcal"
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        titleContentColor = afya.ink,
        textContentColor = afya.inkMuted,
        title = { Text(stringResource(R.string.plan_water_goal) + " · $label") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                label = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toIntOrNull()?.let(onSave)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.save_field), color = afya.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = afya.inkMuted) }
        },
    )
}

private fun directionLabel(direction: Targets.Direction): Int = when (direction) {
    Targets.Direction.LOSE -> R.string.plan_direction_lose
    Targets.Direction.MAINTAIN -> R.string.plan_direction_maintain
    Targets.Direction.GAIN -> R.string.plan_direction_gain
}

/**
 * What the person said they are after, and what the plan therefore does about it. A written goal
 * weight outranks the stated aim, so the card says which of the two the energy figure is following.
 */
@Composable
private fun IntentCard(
    intent: IntentProfile,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    AfyaCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(image = AfyaIcons.Flag, hue = afya.brand, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.intent_heading), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = intent.aim?.label ?: stringResource(R.string.intent_unset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = afya.inkMuted,
                )
            }
            TextAction(
                label = stringResource(if (intent.answered) R.string.intent_change else R.string.intent_answer),
                onClick = onEdit,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (!intent.answered) {
            Text(
                text = stringResource(R.string.intent_asked),
                style = MaterialTheme.typography.bodyMedium,
                color = afya.inkMuted,
            )
            return@AfyaCard
        }
        Hairline()
        Spacer(Modifier.height(10.dp))
        intent.aim?.line?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = afya.ink)
            Spacer(Modifier.height(10.dp))
        }
        // The pace sentence is too long for a value column; the figure it stands for is not.
        intent.pace?.let {
            ValueRow(stringResource(R.string.intent_row_pace), "${it.label} · ${it.weekKg} kg/week")
        }
        intent.motivation?.let { ValueRow(stringResource(R.string.intent_row_why), it.label) }
        intent.obstacle?.let { ValueRow(stringResource(R.string.intent_row_obstacle), it.label) }
        intent.checkIn?.let { ValueRow(stringResource(R.string.intent_row_checkin), it.label) }
    }
}

/**
 * The interview: five questions, one on screen at a time, every answer a pill rather than a typed
 * word. Next only unlocks once the question is answered, and the whole set is written in one go.
 */
@Composable
private fun IntentInterview(
    start: IntentProfile,
    stage: Int,
    onStage: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: (IntentProfile) -> Unit,
) {
    val afya = MaterialTheme.afya
    var aim by remember { mutableStateOf(start.aim) }
    var pace by remember { mutableStateOf(start.pace) }
    var why by remember { mutableStateOf(start.motivation) }
    var block by remember { mutableStateOf(start.obstacle) }
    var habit by remember { mutableStateOf(start.checkIn) }

    val answered = when (stage) {
        0 -> aim != null
        1 -> pace != null
        2 -> why != null
        3 -> block != null
        else -> habit != null
    }
    val last = stage == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        titleContentColor = afya.ink,
        textContentColor = afya.inkMuted,
        title = { Text(stringResource(questionTitle(stage))) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.intent_progress, stage + 1, 5),
                    style = SectionHeader,
                    color = afya.inkMuted,
                )
                Spacer(Modifier.height(14.dp))
                when (stage) {
                    0 -> AnswerGroup(Aim.entries.toList(), aim, { it.label }) { aim = it }
                    1 -> AnswerGroup(Pace.entries.toList(), pace, { it.label }) { pace = it }
                    2 -> AnswerGroup(Motivation.entries.toList(), why, { it.label }) { why = it }
                    3 -> AnswerGroup(Obstacle.entries.toList(), block, { it.label }) { block = it }
                    else -> AnswerGroup(CheckIn.entries.toList(), habit, { it.label }) { habit = it }
                }
                val note = when (stage) {
                    0 -> aim?.line
                    1 -> pace?.line
                    else -> null
                }
                if (note != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = afya.inkMuted)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = answered,
                onClick = {
                    if (last) {
                        onSave(IntentProfile(aim, pace, why, block, habit))
                    } else {
                        onStage(stage + 1)
                    }
                },
            ) {
                Text(
                    stringResource(if (last) R.string.intent_save else R.string.intent_next),
                    color = if (answered) afya.brand else afya.inkFaint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (stage > 0) onStage(stage - 1) else onDismiss() }) {
                Text(
                    stringResource(if (stage > 0) R.string.intent_back else R.string.cancel),
                    color = afya.inkMuted,
                )
            }
        },
    )
}

/** One question's answer set: pills that wrap, and nothing selectable until it is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> AnswerGroup(
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            ChoicePill(
                label = labelOf(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@StringRes
private fun questionTitle(stage: Int): Int = when (stage) {
    0 -> R.string.intent_q_aim
    1 -> R.string.intent_q_pace
    2 -> R.string.intent_q_why
    3 -> R.string.intent_q_obstacle
    else -> R.string.intent_q_checkin
}

/** What was eaten today, in the person's own words. The energy line only appears when it was typed. */
@Composable
private fun MealCard(
    rows: List<ReadingsDb.Meal>,
    loggedKcal: Int?,
    goalKcal: Int,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    val hue = afya.hue("bmr")
    AfyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHead(
            title = stringResource(R.string.plan_food),
            action = {
                Text(
                    text = pluralStringResource(R.plurals.schedule_rows, rows.size, rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = afya.inkMuted,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        TonalAction(
            label = stringResource(R.string.plan_food_add),
            onClick = onAdd,
            icon = AfyaIcons.Food,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.plan_food_empty),
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
            )
        } else {
            rows.forEachIndexed { index, meal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(hue.copy(alpha = afya.tileAlpha)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AfyaIcons.Restaurant,
                            contentDescription = null,
                            tint = hue,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(meal.food, style = MaterialTheme.typography.bodyMedium, color = afya.ink)
                        Text(
                            text = "${meal.amount}  ·  ${DateFmt.clock(meal.timestampMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = afya.inkMuted,
                        )
                    }
                    if (meal.kilocalories != null) {
                        Text(
                            text = stringResource(R.string.plan_food_kcal_value, meal.kilocalories),
                            style = MaterialTheme.typography.labelMedium,
                            color = hue,
                        )
                    }
                    // Only the bin deletes. A row that removed itself on any tap would lose a meal
                    // to the pocket on the way past the card.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = stringResource(R.string.plan_food_remove)) {
                                onDelete(meal.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AfyaIcons.Trash,
                            contentDescription = null,
                            tint = afya.inkFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (index < rows.lastIndex) Hairline()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (loggedKcal != null) {
                    stringResource(R.string.plan_food_against_goal, loggedKcal, goalKcal)
                } else {
                    stringResource(R.string.plan_food_total_none)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (loggedKcal != null) hue else afya.inkMuted,
            )
        }
    }
}

/** Name and amount are theirs; energy is only ever what they typed, never a figure invented here. */
@Composable
private fun MealDialog(
    onSave: (String, String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val afya = MaterialTheme.afya
    var food by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    val ready = food.isNotBlank() && amount.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        titleContentColor = afya.ink,
        textContentColor = afya.inkMuted,
        title = { Text(stringResource(R.string.plan_food_dialog)) },
        text = {
            Column {
                OutlinedTextField(
                    value = food,
                    onValueChange = { food = it.take(60) },
                    label = { Text(stringResource(R.string.plan_food_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(30) },
                    label = { Text(stringResource(R.string.plan_food_amount)) },
                    placeholder = { Text(stringResource(R.string.plan_food_amount_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.plan_food_kcal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.plan_food_kcal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = afya.inkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    onSave(food.trim(), amount.trim(), kcal.toIntOrNull()?.takeIf { it > 0 })
                },
            ) {
                Text(
                    stringResource(R.string.plan_food_save),
                    color = if (ready) afya.brand else afya.inkFaint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = afya.inkMuted)
            }
        },
    )
}
