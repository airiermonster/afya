package com.maximillianurio.afya.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.R

/**
 * The drawings for the moments with nothing to show yet. They are Storyset "Bro" illustrations
 * (storyset.com, credited on the About card and in NOTICE.md), recoloured to the brand pine teal.
 * Each sits on its own pale backdrop, so one set reads on light and dark cards alike and nobody
 * drawn in them is recoloured for the theme.
 */
enum class Scene(@param:DrawableRes val drawable: Int) {
    /** No weigh-in yet: someone out running with a tracker on their wrist. */
    FirstWeighIn(R.drawable.art_fitness_tracker),

    /** Nothing filed yet: a phone of figures beside someone skipping. */
    Records(R.drawable.art_fitness_stats),

    /** No projection yet: a stairway to a goal. */
    Heading(R.drawable.art_personal_goals),

    /** Nothing drunk yet today. */
    Glass(R.drawable.art_bottle_of_water),

    /** No food written in yet today. */
    Meal(R.drawable.art_eating_healthy_food),

    /** The Bluetooth permission is still needed. */
    Permission(R.drawable.art_privacy_policy),
}

/** A scene at [size], square like the source artwork. Decorative: the text beside it says what it means. */
@Composable
fun Illustration(scene: Scene, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    Image(
        painter = painterResource(scene.drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
