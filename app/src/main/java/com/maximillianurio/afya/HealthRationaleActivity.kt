package com.maximillianurio.afya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximillianurio.afya.ui.brand.AfyaLogo
import com.maximillianurio.afya.ui.components.PrimaryAction
import com.maximillianurio.afya.ui.theme.AfyaTheme
import com.maximillianurio.afya.ui.theme.afya

/**
 * What Health Connect shows when the person asks why Afya wants to write their weight: exactly what
 * is shared, what is not, and that nothing leaves the phone because of it.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AfyaTheme(darkTheme = isSystemInDarkTheme()) {
                val afya = MaterialTheme.afya
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(afya.canvas)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    AfyaLogo(size = 56.dp, description = stringResource(R.string.app_name))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.health_rationale_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = afya.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.health_rationale_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = afya.inkMuted,
                    )
                    Spacer(Modifier.height(28.dp))
                    PrimaryAction(label = stringResource(R.string.health_rationale_close), onClick = { finish() })
                }
            }
        }
    }
}
