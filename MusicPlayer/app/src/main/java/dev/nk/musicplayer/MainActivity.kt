package dev.nk.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import dev.nk.musicplayer.ui.AppNavigation
import dev.nk.musicplayer.ui.permission.PermissionGate
import dev.nk.musicplayer.ui.theme.AmbientLight
import dev.nk.musicplayer.ui.theme.MusicPlayerTheme
import dev.nk.musicplayer.ui.theme.accents

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as MusicApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings = container.settingsStore
            val theme by settings.theme.collectAsStateWithLifecycle()
            val glowEnabled by settings.glowEnabled.collectAsStateWithLifecycle()

            MusicPlayerTheme(theme = theme, glowEnabled = glowEnabled) {
                CompositionLocalProvider(LocalContainer provides container) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // The ambient light sits behind everything and outside the safe-area
                        // inset, so the drifting orbs run under the status and nav bars.
                        AmbientLight(
                            accents = theme.accents(),
                            enabled = glowEnabled && !theme.isLight
                        )
                        // API 35 forces edge-to-edge, so the Surface paints the whole window
                        // and the content is inset off the status and navigation bars.
                        Box(modifier = Modifier.safeDrawingPadding()) {
                            PermissionGate { AppNavigation() }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Binding here (rather than in the Application) means the service is free to stop
        // once nothing is playing and the UI is gone.
        container.playerConnection.connect()
    }

    override fun onStop() {
        container.playerConnection.release()
        super.onStop()
    }
}
