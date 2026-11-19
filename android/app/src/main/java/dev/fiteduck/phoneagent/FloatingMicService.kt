package dev.fiteduck.phoneagent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val CHANNEL_ID = "voice"
private const val NOTIFICATION_ID = 1
private const val EDGE_MARGIN_DP = 12
private const val ACTION_STOP = "dev.fiteduck.phoneagent.STOP"

/**
 * Keeps the floating mic on screen over every app.
 *
 * - Android only lets an app record while it has a window or a microphone foreground service.
 * - This service has both, so the mic works without the app being open.
 * - The notification's Stop button removes the mic and ends the service.
 */
class FloatingMicService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var floatingMic: FloatingMicView

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        val host = HostClient(BuildConfig.HOST_URL, BuildConfig.HOST_TOKEN)
        val pushToTalk = PushToTalk(scope, host) { floatingMic.show(it) }
        floatingMic = FloatingMicView(this, onPress = { pressed(pushToTalk) }, onRelease = pushToTalk::released)
        windowManager = getSystemService(WindowManager::class.java)
        windowManager.addView(floatingMic, overlayParams())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        windowManager.removeView(floatingMic)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pressed(pushToTalk: PushToTalk) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pushToTalk.pressed()
        } else {
            floatingMic.show(Status.Failed("allow the microphone in the app first"))
        }
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = (EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
        y = 600
    }

    private fun notification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Voice", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val stop = PendingIntent.getService(
            this, 0, Intent(this, FloatingMicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle("Floating mic is on")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingMicService::class.java))
        }
    }
}
