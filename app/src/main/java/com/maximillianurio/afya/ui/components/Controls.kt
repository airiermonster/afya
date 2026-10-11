package com.maximillianurio.afya.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.ui.icons.AfyaIcons
import com.maximillianurio.afya.ui.theme.afya

/**
 * A control answering a press: a two percent squeeze and nothing else. The ripple is dropped on
 * purpose, because these surfaces already sit off the ground and a second signal reads as noise.
 */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = if (pressed) 0.98f else 1f
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The one filled action on a surface: 56 dp tall, brand-filled, deep corners, white label. */
@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val afya = MaterialTheme.afya
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    // A disabled action sinks into the sheet rather than fading a saturated fill to mud.
    val ground = if (enabled) afya.brand else afya.cardTint
    val ink = if (enabled) afya.onBrand else afya.inkFaint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .pressScale(source)
            .background(ground, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** A secondary action: the brand stain rather than the brand itself. */
@Composable
fun TonalAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val afya = MaterialTheme.afya
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, afya.hairline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = afya.card),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = afya.brandDeep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = afya.brandDeep)
    }
}

/** A plain text action, for the places a button would shout. */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.afya.brand,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone)
    }
}

/** A small selectable pill. Pills are reserved for these and never used for cards. */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) afya.brand else afya.cardTint)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) afya.onBrand else afya.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Mutually exclusive pills that wrap onto as many lines as the labels need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
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

/** A status word with its dot. The word always carries the meaning; the colour only repeats it. */
@Composable
fun StatusPill(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val afya = MaterialTheme.afya
    Box(
        modifier = modifier
            .background(tone.copy(alpha = afya.tileAlpha), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).background(tone, RoundedCornerShape(50)))
            Text(text, style = MaterialTheme.typography.labelSmall, color = tone)
        }
    }
}

/** A tappable line: glyph, title, optional subtitle, and a trailing slot or a chevron. */
@Composable
fun ListRow(
    title: String,
    hue: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val afya = MaterialTheme.afya
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(image = icon, hue = hue, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = afya.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = afya.inkMuted)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                imageVector = AfyaIcons.Chevron,
                contentDescription = null,
                tint = afya.inkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A setting that switches something. The switch stays the Material one on purpose. */
@Composable
fun ToggleRow(
    title: String,
    hue: Color,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val afya = MaterialTheme.afya
    // The whole row is the switch, so TalkBack reads the title and the state as one control.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(image = icon, hue = hue, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = afya.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = afya.inkMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = afya.brand,
                checkedThumbColor = afya.onBrand,
                uncheckedTrackColor = afya.cardTint,
                uncheckedThumbColor = afya.inkFaint,
                uncheckedBorderColor = afya.hairline,
            ),
        )
    }
}

/** A decision that has to interrupt. Everything else uses the bar. */
@Composable
fun AfyaDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val afya = MaterialTheme.afya
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = afya.ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = afya.inkMuted) },
        shape = RoundedCornerShape(28.dp),
        containerColor = afya.card,
        titleContentColor = afya.ink,
        textContentColor = afya.inkMuted,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) afya.bandHigh else afya.brand,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = MaterialTheme.typography.labelLarge, color = afya.inkMuted)
            }
        },
    )
}
