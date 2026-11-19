package dev.fiteduck.phoneagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope

private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

class MainActivity : ComponentActivity() {
    private var status: Status by mutableStateOf(Status.Idle)
    private lateinit var pushToTalk: PushToTalk

    private val askForPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { showFloatingMic() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = HostClient(BuildConfig.HOST_URL, BuildConfig.HOST_TOKEN)
        pushToTalk = PushToTalk(lifecycleScope, host) { status = it }
        setContent {
            MaterialTheme {
                PushToTalkScreen(
                    status,
                    onPress = ::startIfAllowed,
                    onRelease = pushToTalk::released,
                    onShowFloatingMic = ::onShowFloatingMic,
                )
            }
        }
    }

    /** The first press only asks for permission, so the user holds again once it is granted. */
    private fun startIfAllowed() {
        if (isGranted(Manifest.permission.RECORD_AUDIO)) {
            pushToTalk.pressed()
        } else {
            askForPermissions.launch(PERMISSIONS)
        }
    }

    private fun onShowFloatingMic() {
        if (PERMISSIONS.all(::isGranted)) showFloatingMic() else askForPermissions.launch(PERMISSIONS)
    }

    private fun showFloatingMic() {
        if (Settings.canDrawOverlays(this)) {
            FloatingMicService.start(this)
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun isGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
