package dev.nk.musicplayer.ui.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import dev.nk.musicplayer.util.audioPermission
import dev.nk.musicplayer.util.hasAudioPermission

private const val PREFS = "permission_state"
private const val KEY_ASKED = "asked_audio"

/**
 * Shows [content] once the read-audio permission is granted, and otherwise walks through
 * rationale -> request -> "you denied this permanently, here is Settings".
 *
 * The "asked before" flag is persisted because `shouldShowRequestPermissionRationale` returns
 * false both on a fresh install *and* after a permanent denial, and those two need different UI.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var granted by remember { mutableStateOf(context.hasAudioPermission()) }
    var askedBefore by remember { mutableStateOf(prefs.getBoolean(KEY_ASKED, false)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedBefore = true
        prefs.edit { putBoolean(KEY_ASKED, true) }
    }

    // Coming back from Settings with the permission toggled on should just work.
    LaunchedEffect(Unit) {
        if (!granted) granted = context.hasAudioPermission()
    }

    if (granted) {
        content()
        return
    }

    val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
    // Asked at least once, and the system will no longer show the dialog -> "Don't allow" twice.
    val permanentlyDenied = askedBefore && !canShowRationale

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Access to your music",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (permanentlyDenied) {
                "Audio access is turned off for this app. Enable it in Settings and come back — " +
                    "nothing is uploaded, the library is read on-device only."
            } else {
                "This app reads the audio files already on your phone so it can list and play them. " +
                    "Nothing leaves the device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (permanentlyDenied) {
            Button(onClick = { activity.openAppSettings() }) { Text("Open settings") }
            TextButton(onClick = { granted = context.hasAudioPermission() }) { Text("I've enabled it") }
        } else {
            Button(onClick = { launcher.launch(audioPermission) }) { Text("Grant access") }
        }
    }
}

private fun Activity.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
