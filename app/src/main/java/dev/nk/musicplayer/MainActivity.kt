package dev.nk.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.nk.musicplayer.ui.AppNavigation
import dev.nk.musicplayer.ui.permission.PermissionGate
import dev.nk.musicplayer.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as MusicApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PermissionGate { AppNavigation() }
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
