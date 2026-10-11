package com.maximillianurio.afya.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximillianurio.afya.ui.theme.AfyaMotion
import com.maximillianurio.afya.ui.theme.afya
import com.maximillianurio.afya.ui.theme.reducedMotion
import kotlin.math.max

/**
 * The ring: a value drawn as an arc that sweeps out and settles. It carries the app's one authored
 * moment on Measure and reads quietly everywhere else, which is why the same component takes the
 * duration instead of growing a second behaviour.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    hue: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 96.dp,
    stroke: Dp = 10.dp,
    sweepDegrees: Float = 360f,
    startAngle: Float = -90f,
    durationMillis: Int = 900,
    spring: Boolean = false,
    settling: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    val afya = MaterialTheme.afya
    val reduced = reducedMotion()
    val target = fraction.coerceIn(0f, 1f)
    // The first time a ring appears it draws out from nothing; after that it moves from where it is
    // to the new value, so logging a glass of water grows the ring rather than restarting it.
    val progress = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        when {
            reduced -> progress.snapTo(target)
            // Underdamped: the arc runs a hair past the value and settles back onto it.
            spring -> progress.animateTo(target, AfyaMotion.sweep())
            else -> progress.animateTo(target, tween(durationMillis, easing = LinearOutSlowInEasing))
        }
    }
    // While a load is settling the ring has no value to show yet, so a short arc turns instead:
    // the scale is working, and nothing is claimed about the figure until it locks.
    val turn = if (settling && !reduced) {
        rememberInfiniteTransition(label = "ring-settle").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
            label = "ring-turn",
        ).value
    } else {
        0f
    }
    val at = progress.value.coerceIn(0f, 1f)
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = stroke.toPx() / 2
            val drawn = Size(size.width - stroke.toPx(), size.height - stroke.toPx())
            drawArc(
                color = afya.cardTint,
                startAngle = startAngle,
                sweepAngle = sweepDegrees,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = drawn,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
            )
            if (settling) {
                drawArc(
                    color = hue.copy(alpha = 0.85f),
                    startAngle = startAngle + turn,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = drawn,
                    style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
                )
                return@Canvas
            }
            val sweep = sweepDegrees * at
            if (sweep > 0.4f) {
                drawArc(
                    color = hue,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = drawn,
                    style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        if (content != null) content()
    }
}

/**
 * A reading's history as an area: the line draws itself from left to right, the wash rises under it,
 * and the latest point is marked. Points are time/value pairs in ascending time order.
 *
 * With a [trend] the smoothed line takes the stroke and the readings drop back to a faint trace with
 * a dot per weigh-in, so the eye follows the body rather than the day's water.
 */
@Composable
fun TrendArea(
    points: List<Pair<Long, Double>>,
    hue: Color,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    projection: Pair<Long, Double>? = null,
    trend: List<Pair<Long, Double>>? = null,
    chartHeight: Dp = 160.dp,
    emptyLabel: String = "",
    description: String? = null,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val afya = MaterialTheme.afya
    val reduced = reducedMotion()
    val data = points.filter { it.second.isFinite() }
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(data.size, reduced) {
        if (reduced) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
        }
    }

    val smooth = trend?.filter { it.second.isFinite() }?.takeIf { it.size >= 2 }
    // A goal far outside the readings would squash them into a flat line along one edge, so it is only
    // drawn when it is close enough to share the scale with them.
    val shownGoal = goal?.takeIf { target ->
        if (data.isEmpty()) return@takeIf false
        val lo = data.minOf { it.second }
        val hi = data.maxOf { it.second }
        val gap = if (target < lo) lo - target else if (target > hi) target - hi else 0.0
        gap <= maxOf(hi - lo, 2.0)
    }
    val chartSemantics = if (description != null && data.size >= 2) {
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth().height(chartHeight).then(chartSemantics)) {
        if (data.size < 2) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = afya.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Canvas(Modifier.fillMaxSize()) {
            val padRight = 6.dp.toPx()
            val left = 2.dp.toPx()
            val right = size.width - padRight
            val top = 10.dp.toPx()
            val bottom = size.height - 12.dp.toPx()

            repeat(3) { i ->
                val y = top + (bottom - top) * i / 2f
                drawLine(afya.hairline, Offset(left, y), Offset(right, y), 1.dp.toPx())
            }

            val values = data.map { it.second } +
                smooth.orEmpty().map { it.second } +
                listOfNotNull(shownGoal, projection?.second)
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 0.5) {
                val centre = (hi + lo) / 2
                lo = centre - 0.5
                hi = centre + 0.5
            }
            val t0 = data.first().first.toDouble()
            val tEnd = max(data.last().first.toDouble(), projection?.first?.toDouble() ?: 0.0)
            val span = max(tEnd - t0, 1.0)

            fun xAt(time: Long) = left + (right - left) * ((time - t0) / span).toFloat()
            fun yAt(value: Double) = bottom - (bottom - top) * ((value - lo) / (hi - lo)).toFloat()

            val line = Path().apply {
                data.forEachIndexed { index, point ->
                    val x = xAt(point.first)
                    val y = yAt(point.second)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(xAt(data.last().first), bottom)
                lineTo(xAt(data.first().first), bottom)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(hue.copy(alpha = (if (smooth != null) 0.14f else 0.26f) * reveal.value), Color.Transparent),
                    startY = top,
                    endY = bottom,
                ),
            )
            if (smooth == null) {
                drawRevealed(line, hue, 2.6.dp.toPx(), reveal.value)
            } else {
                drawRevealed(line, hue.copy(alpha = 0.38f), 1.2.dp.toPx(), reveal.value)
                val shown = (data.size * reveal.value).toInt()
                data.take(shown).forEach { point ->
                    drawCircle(hue.copy(alpha = 0.55f), radius = 2.2.dp.toPx(), center = Offset(xAt(point.first), yAt(point.second)))
                }
                val smoothLine = Path().apply {
                    smooth.forEachIndexed { index, point ->
                        val x = xAt(point.first)
                        val y = yAt(point.second)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawRevealed(smoothLine, hue, 2.8.dp.toPx(), reveal.value)
            }

            if (shownGoal != null) {
                val y = yAt(shownGoal)
                drawLine(
                    color = afya.brandDeep,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), 0f),
                )
            }

            if (projection != null && reveal.value > 0.97f) {
                val start = Offset(xAt(data.last().first), yAt(data.last().second))
                val end = Offset(xAt(projection.first), yAt(projection.second))
                drawLine(
                    color = afya.inkMuted,
                    start = start,
                    end = end,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()), 0f),
                )
                drawCircle(
                    color = afya.inkMuted,
                    radius = 4.dp.toPx(),
                    center = end,
                    style = Stroke(width = 1.8.dp.toPx()),
                )
            }

            val last = Offset(xAt(data.last().first), yAt(data.last().second))
            drawCircle(hue, radius = 4.4.dp.toPx(), center = last)
            drawCircle(afya.card, radius = 1.8.dp.toPx(), center = last)
        }
    }
    if (data.size >= 2 && (startLabel != null || endLabel != null)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                text = startLabel.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = afya.inkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = endLabel.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = afya.inkMuted,
            )
        }
    }
    }
}


/** Draws [path] in [color] from its start up to [fraction] of its length, for the line-drawing reveal. */
private fun DrawScope.drawRevealed(path: Path, color: Color, width: Float, fraction: Float) {
    if (fraction <= 0f) return
    val length = PathMeasure().apply { setPath(path, false) }.length
    if (length <= 0f) return
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(length, length),
                length * (1f - fraction),
            ),
        ),
    )
}

/**
 * Where a figure sits inside its reference range: the healthy span printed on the track, the marker
 * riding it. The marker position comes from the report, which already normalises the value.
 */
@Composable
fun BandBar(
    position: Float,
    tone: Color,
    modifier: Modifier = Modifier,
    range: Pair<Float, Float>? = null,
) {
    val afya = MaterialTheme.afya
    val reduced = reducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(progress) {
        if (!reduced) progress.animateTo(1f, AfyaMotion.enter())
    }
    val at = progress.value
    val marker = position.coerceIn(0f, 1f)
    val span = range ?: (0f to 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val trackHeight = 5.dp.toPx()
        val y = (size.height - trackHeight) / 2f
        drawRoundRect(
            color = afya.cardTint,
            topLeft = Offset(0f, y),
            size = Size(size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2, trackHeight / 2),
        )
        val x0 = size.width * span.first.coerceIn(0f, 1f)
        val x1 = size.width * span.second.coerceIn(0f, 1f)
        drawRoundRect(
            color = tone.copy(alpha = 0.30f),
            topLeft = Offset(x0, y),
            size = Size(max(x1 - x0, trackHeight) * at, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2, trackHeight / 2),
        )
        drawCircle(
            color = tone,
            radius = 5.dp.toPx() * at,
            center = Offset(size.width * marker, size.height / 2f),
        )
    }
}
