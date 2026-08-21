package dev.nk.musicplayer.ui.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.nk.musicplayer.util.hasPermission
import dev.nk.musicplayer.util.notificationPermission

/**
 * Asks once for POST_NOTIFICATIONS so the media notification can appear on API 33+.
 * Deliberately non-blocking: refusing it costs the notification, not playback.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current
    val permission = notificationPermission ?: return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!context.hasPermission(permission)) launcher.launch(permission)
    }
}
