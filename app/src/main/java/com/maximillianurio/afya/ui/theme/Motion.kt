package com.maximillianurio.afya.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app has one authored moment — the lock, when a reading settles, its ring closes and the
 * scale's own figure takes one small breath. Everything else moves on the same short, exponential
 * ease-out so the interface feels like one material rather than a set of effects.
 */
object AfyaMotion {
    /** The lock: a ring that overshoots a hair and settles, the way a real needle does. */
    fun <T> sweep(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = 0.68f, stiffness = 240f)

    /** A card lifting into place. */
    fun <T> enter(delayMillis: Int = 0): androidx.compose.animation.core.TweenSpec<T> =
        tween(durationMillis = 380, delayMillis = delayMillis, easing = FastOutSlowInEasing)

    /** The gap between one card and the next in a staggered group. */
    const val Stagger = 45

    /** A control answering a press. */
    fun <T> press(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f)
}

/**
 * True when the system asks for no animation. Every animated value in the app reads this and jumps
 * to its end state instead, so the setting is honoured rather than decorated over.
 */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
