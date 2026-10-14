package com.r17e.guide.session

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.r17e.guide.R17eApp
import com.r17e.guide.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground (microphone) service that keeps a helper session alive while the user switches apps. */
class GuideSessionService : Service() {
    private var controller: GuideController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startInForeground()
        if (controller == null) {
            val app = application as R17eApp
            controller = app.newController { _state.value = it }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Helper", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GuideSessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("R17E helper is listening")
            .setContentText("Talk to me. I will show you where to tap.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        controller?.stop(); controller = null
        _state.value = GuideController.State.STOPPED
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "r17e_helper"
        private const val NOTIF_ID = 17
        private const val ACTION_STOP = "com.r17e.guide.STOP"
        private val _state = MutableStateFlow(GuideController.State.STOPPED)
        val state: StateFlow<GuideController.State> = _state

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, GuideSessionService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GuideSessionService::class.java))
    }
}
