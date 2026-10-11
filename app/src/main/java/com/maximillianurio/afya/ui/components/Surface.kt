package com.maximillianurio.afya.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.ui.theme.AfyaMotion
import com.maximillianurio.afya.ui.theme.afya
import com.maximillianurio.afya.ui.theme.reducedMotion
import kotlinx.coroutines.delay

/**
 * The one container in the app: white, 28 dp corners, one soft wide shadow, 20 dp of air inside.
 * `tonal` gives the inset variant used for wells inside a card, which carries no shadow of its own
 * so nesting never stacks two elevations.
 */
@Composable
fun AfyaCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val afya = MaterialTheme.afya
    val shape = RoundedCornerShape(if (tonal) 20.dp else 28.dp)
    val surface = if (tonal) afya.cardTint else afya.card
    Column(
        modifier = modifier
            .then(
                if (tonal) {
                    Modifier
                } else {
                    Modifier.shadow(16.dp, shape, ambientColor = afya.shadow, spotColor = afya.shadow)
                },
            )
            .background(surface, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** The heading of a group. It is the heading itself, not an eyebrow sitting above another one. */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    val afya = MaterialTheme.afya
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = afya.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null) action()
    }
}

/**
 * The tinted square that carries a glyph large enough to read at arm's length. `filled` inverts it
 * for the one primary action on a page; a hue at low alpha stains the ground for everything else.
 */
@Composable
fun IconTile(
    image: ImageVector,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    filled: Boolean = false,
) {
    val afya = MaterialTheme.afya
    val shape = RoundedCornerShape(size * 0.32f)
    val ground = if (filled) hue else hue.copy(alpha = afya.tileAlpha)
    val glyph = if (filled) afya.card else hue
    Box(
        modifier = modifier.size(size).background(ground, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** A quiet seam inside one group. Never a border on the outside of a card. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.afya.hairline),
    )
}

/** What a screen shows when there is nothing to show: the missing thing, and the one action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    image: ImageVector? = null,
    scene: Scene? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val afya = MaterialTheme.afya
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (scene != null) {
            Illustration(scene)
            Spacer(Modifier.height(18.dp))
        } else if (image != null) {
            Icon(
                imageVector = image,
                contentDescription = null,
                tint = afya.brand,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = afya.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = afya.inkMuted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Lifts content into place once, when it first appears, staggered by [index] within its group. The
 * system's Reduce animations setting cuts it to an instant reveal.
 */
@Composable
fun Modifier.enterLift(index: Int): Modifier {
    val reduced = reducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(progress) {
        if (!reduced) {
            delay(index.toLong() * AfyaMotion.Stagger)
            progress.animateTo(1f, AfyaMotion.enter())
        }
    }
    val at = progress.value
    return graphicsLayer {
        alpha = at
        translationY = (1f - at) * 20f
    }
}
