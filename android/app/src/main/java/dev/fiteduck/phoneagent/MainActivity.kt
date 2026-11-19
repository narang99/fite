package dev.fiteduck.phoneagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private lateinit var pushToTalk: PushToTalk

    private val askForMicrophone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = HostClient(BuildConfig.HOST_URL, BuildConfig.HOST_TOKEN)
        pushToTalk = PushToTalk(lifecycleScope, host)
        setContent {
            MaterialTheme {
                PushToTalkScreen(
                    pushToTalk.status,
                    onPress = ::startIfAllowed,
                    onRelease = pushToTalk::released,
                )
            }
        }
    }

    /** The first press only asks for permission, so the user holds again once it is granted. */
    private fun startIfAllowed() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pushToTalk.pressed()
        } else {
            askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
