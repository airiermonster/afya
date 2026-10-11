package com.maximillianurio.afya

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximillianurio.afya.app.AfyaViewModel
import com.maximillianurio.afya.ui.AfyaApp
import com.maximillianurio.afya.ui.theme.AfyaTheme
import com.maximillianurio.afya.ui.theme.afyaUsesNight

/**
 * Shell only: the Views build kept the scanner, the store and the database on the activity, while
 * here [AfyaViewModel] owns them and survives rotation.
 */
class MainActivity : ComponentActivity() {

    /**
     * Bumped each time a launcher shortcut or the Quick Settings tile asks for the platform, including
     * when the app is already open, so the shell can switch tabs on every request and not only the first.
     */
    private val weighInRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.isWeighIn()) weighInRequests.intValue++
        setContent {
            val vm: AfyaViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val night = afyaUsesNight(state.settings.themeMode, isSystemInDarkTheme())
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            SideEffect {
                bars.isAppearanceLightStatusBars = !night
                bars.isAppearanceLightNavigationBars = !night
            }
            AfyaTheme(darkTheme = night) { AfyaApp(vm, weighInRequests = weighInRequests.intValue) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isWeighIn()) weighInRequests.intValue++
    }

    private fun Intent.isWeighIn(): Boolean = action == ACTION_WEIGH_IN

    companion object {
        const val ACTION_WEIGH_IN = "com.maximillianurio.afya.action.WEIGH_IN"

        fun weighInIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_WEIGH_IN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
