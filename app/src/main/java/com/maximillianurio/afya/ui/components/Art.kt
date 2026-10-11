package com.maximillianurio.afya.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.ui.theme.AfyaColors
import com.maximillianurio.afya.ui.theme.afya
import com.maximillianurio.afya.ui.theme.reducedMotion

/**
 * The app's own drawings for the moments with nothing to show yet. They are drawn here rather than
 * shipped as bitmaps so they take the theme's colours in light and dark, scale to any density, and
 * share the icon set's line: a brand stroke over a brand-wash fill, on a faint ground line. Each one
 * draws itself in once, the way the charts do.
 */
enum class Scene { FirstWeighIn, Heading, Glass }

@Composable
fun Illustration(scene: Scene, modifier: Modifier = Modifier, width: Dp = 168.dp, height: Dp = 112.dp) {
    val afya = MaterialTheme.afya
    val reduced = reducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (!reduced) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.size(width, height)) {
        val unit = size.width / 168f
        when (scene) {
            Scene.FirstWeighIn -> firstWeighIn(afya, unit, reveal.value)
            Scene.Heading -> heading(afya, unit, reveal.value)
            Scene.Glass -> glass(afya, unit, reveal.value)
        }
    }
}

private fun DrawScope.line(afya: AfyaColors, unit: Float) =
    Stroke(width = 2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.ground(afya: AfyaColors, unit: Float) {
    drawLine(
        color = afya.hairline,
        start = Offset(12f * unit, 104f * unit),
        end = Offset(156f * unit, 104f * unit),
        strokeWidth = 1.6f * unit,
        cap = StrokeCap.Round,
    )
}

/** The plinth, its four electrodes, and the first point of a line that has not been drawn yet. */
private fun DrawScope.firstWeighIn(afya: AfyaColors, unit: Float, at: Float) {
    ground(afya, unit)
    val top = 70f * unit
    drawRoundRect(
        color = afya.brandWash,
        topLeft = Offset(30f * unit, top),
        size = Size(108f * unit, 28f * unit),
        cornerRadius = CornerRadius(10f * unit),
    )
    drawRoundRect(
        color = afya.brand,
        topLeft = Offset(30f * unit, top),
        size = Size(108f * unit, 28f * unit),
        cornerRadius = CornerRadius(10f * unit),
        style = line(afya, unit),
    )
    listOf(46f, 64f, 104f, 122f).forEach { x ->
        drawCircle(afya.brand, radius = 3.2f * unit, center = Offset(x * unit, top + 14f * unit))
    }
    // The trend to come: dotted, rising, and stopping at the one point it has so far.
    val path = Path().apply {
        moveTo(40f * unit, 52f * unit)
        cubicTo(70f * unit, 50f * unit, 92f * unit, 30f * unit, 128f * unit, 22f * unit)
    }
    drawPath(
        path = path,
        color = afya.brand.copy(alpha = 0.55f * at),
        style = Stroke(
            width = 2f * unit,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f * unit), 0f),
        ),
    )
    drawCircle(afya.brand, radius = 6f * unit * at, center = Offset(40f * unit, 52f * unit))
    drawCircle(afya.card, radius = 2.4f * unit * at, center = Offset(40f * unit, 52f * unit))
}

/** A path from where the readings are to a flag on the goal. */
private fun DrawScope.heading(afya: AfyaColors, unit: Float, at: Float) {
    ground(afya, unit)
    drawCircle(afya.brandWash, radius = 26f * unit, center = Offset(128f * unit, 40f * unit))
    val path = Path().apply {
        moveTo(18f * unit, 96f * unit)
        cubicTo(60f * unit, 96f * unit, 50f * unit, 60f * unit, 88f * unit, 62f * unit)
        cubicTo(112f * unit, 64f * unit, 112f * unit, 44f * unit, 122f * unit, 40f * unit)
    }
    drawPath(
        path = path,
        color = afya.brand.copy(alpha = at),
        style = Stroke(
            width = 2.2f * unit,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * unit, 6f * unit), 0f),
        ),
    )
    drawCircle(afya.brand, radius = 5f * unit, center = Offset(18f * unit, 96f * unit))
    // The flag.
    val pole = Offset(126f * unit, 44f * unit)
    drawLine(afya.brand, pole, Offset(126f * unit, 14f * unit), strokeWidth = 2.2f * unit, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(126f * unit, 15f * unit)
        lineTo(146f * unit, 21f * unit)
        lineTo(126f * unit, 28f * unit)
        close()
    }
    drawPath(flag, color = afya.brand.copy(alpha = at))
}

/** An empty glass with the line it is filled to. */
private fun DrawScope.glass(afya: AfyaColors, unit: Float, at: Float) {
    ground(afya, unit)
    val outline = Path().apply {
        moveTo(62f * unit, 20f * unit)
        lineTo(106f * unit, 20f * unit)
        lineTo(100f * unit, 100f * unit)
        lineTo(68f * unit, 100f * unit)
        close()
    }
    val water = Path().apply {
        val surface = 100f - 54f * at
        val inset = (surface - 20f) / 80f * 6f
        moveTo((62f + inset) * unit, surface * unit)
        quadraticTo(84f * unit, (surface - 5f) * unit, (106f - inset) * unit, surface * unit)
        lineTo(100f * unit, 100f * unit)
        lineTo(68f * unit, 100f * unit)
        close()
    }
    drawPath(water, color = afya.brandWash)
    drawPath(outline, color = afya.brand, style = line(afya, unit))
    drawCircle(afya.brand.copy(alpha = 0.5f * at), radius = 2.4f * unit, center = Offset(78f * unit, 70f * unit))
    drawCircle(afya.brand.copy(alpha = 0.5f * at), radius = 1.8f * unit, center = Offset(90f * unit, 82f * unit))
}
