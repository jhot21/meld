package me.jhot.meld

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import me.jhot.meld.ui.navigation.MeldNavGraph
import me.jhot.meld.ui.theme.MeldTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // The foreground service still runs, but its notification won't be visible.
                // The user can grant the permission later via Settings → Notifications.
                Log.w("MainActivity", "POST_NOTIFICATIONS denied — background service notification will be hidden")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission(Build.VERSION.SDK_INT, isGranted)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MeldTheme {
                MeldNavGraph()
            }
        }
    }
}

/**
 * Returns true if the app should request POST_NOTIFICATIONS permission.
 * The permission was added in Android 13 (TIRAMISU, API 33) and only needs to be
 * requested once — skip the request if it is already granted.
 */
internal fun needsNotificationPermission(sdkVersion: Int, isGranted: Boolean): Boolean =
    sdkVersion >= Build.VERSION_CODES.TIRAMISU && !isGranted
