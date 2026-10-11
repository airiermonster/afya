package com.maximillianurio.afya.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.maximillianurio.afya.ui.theme.afya

/**
 * The Afya mark: a single-storey "a" whose bowl is a dial, its needle resting on a reading. It is
 * the product in one glyph — the letter of the name, and the instrument it reads.
 *
 * Drawn on a 48-unit grid. This object is the one source of the geometry: the Compose logo, the PDF
 * banner and `okok/brand/afya-mark.svg` all read from it, and the Android drawables
 * (`ic_launcher_foreground`, `ic_launcher_monochrome`, `ic_mark`) repeat these exact numbers because
 * vector XML cannot import them.
 */
object AfyaMark {
    const val GRID = 48f

    /** The bowl, a ring of radius 11.5 around the grid centre. */
    const val BOWL = "M24,12.5a11.5,11.5 0 1,1 0,23a11.5,11.5 0 1,1 0,-23"
    const val BOWL_WIDTH = 5f

    /** The stem, tangent to the bowl's right side, the full height of the bowl. */
    const val STEM = "M35.5,12.5V35.5"
    const val STEM_WIDTH = 5f

    /** The needle, from the hub to one o'clock. */
    const val NEEDLE = "M24,24L27.5,17.94"
    const val NEEDLE_WIDTH = 3f

    /** The hub the needle turns on. */
    const val HUB_RADIUS = 2.8f

    /** The ground of the app icon: pine teal, lit a shade from the top left. */
    val GroundLight = Color(0xFF14806F)
    val GroundDeep = Color(0xFF0A5850)

    /** The mark itself on that ground: the canvas mint. */
    val Glyph = Color(0xFFE8F1EE)
}

private val bowlPath by lazy { PathParser().parsePathString(AfyaMark.BOWL).toPath() }
private val stemPath by lazy { PathParser().parsePathString(AfyaMark.STEM).toPath() }
private val needlePath by lazy { PathParser().parsePathString(AfyaMark.NEEDLE).toPath() }

/** Draws the glyph in [color], scaled so the 48-unit grid fills [side] pixels starting at [origin]. */
fun DrawScope.drawAfyaMark(color: Color, side: Float, origin: Offset = Offset.Zero) {
    val k = side / AfyaMark.GRID
    translate(origin.x, origin.y) {
        scale(k, k, pivot = Offset.Zero) {
            val round = { width: Float -> Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
            drawPath(bowlPath, color, style = round(AfyaMark.BOWL_WIDTH))
            drawPath(stemPath, color, style = round(AfyaMark.STEM_WIDTH))
            drawPath(needlePath, color, style = round(AfyaMark.NEEDLE_WIDTH))
            drawCircle(color, radius = AfyaMark.HUB_RADIUS, center = Offset(24f, 24f))
        }
    }
}

/**
 * The app icon as it sits on the launcher: the mark in mint on the lit pine-teal tile. Used where
 * the app names itself — the About card, the Health Connect explanation — never as decoration.
 */
@Composable
fun AfyaLogo(modifier: Modifier = Modifier, size: Dp = 56.dp, description: String? = null) {
    Canvas(
        modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(AfyaMark.GroundLight, AfyaMark.GroundDeep),
                start = Offset.Zero,
                end = Offset(this.size.width, this.size.height),
            ),
            cornerRadius = CornerRadius(this.size.width * 0.28f),
        )
        // The launcher draws the 48-unit glyph at 1.5x inside a 108 tile; the same proportion here.
        val side = this.size.width * (72f / 108f)
        val inset = (this.size.width - side) / 2f
        drawAfyaMark(AfyaMark.Glyph, side, Offset(inset, inset))
    }
}

/** The logo and the wordmark set together, the way the PDF banner and the About card carry them. */
@Composable
fun AfyaLockup(
    name: String,
    modifier: Modifier = Modifier,
    logoSize: Dp = 48.dp,
    wordmark: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val afya = MaterialTheme.afya
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AfyaLogo(size = logoSize)
        Text(
            text = name.lowercase(),
            style = wordmark.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
            color = afya.ink,
        )
    }
}
