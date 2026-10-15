package com.r17e.guide.session

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.r17e.guide.R17eApp
import com.r17e.guide.offline.OfflineGuide
import com.r17e.guide.overlay.OverlayPointer
import com.r17e.guide.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground (microphone) service that keeps a helper session alive while the user switches apps. */
class GuideSessionService : Service() {
    private var controller: GuideController? = null
    private var offline: OfflineGuide? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun onState(st: GuideController.State) {
        _state.value = st
        main.post {
            if (st == GuideController.State.OFFLINE && offline == null) {
                offline = OfflineGuide(this, (application as R17eApp).recipes, OverlayPointer, (application as R17eApp).prefs.lang).also { it.start() }
            } else if (st == GuideController.State.LISTENING) { offline?.stop(); offline = null }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Debug builds only: `adb shell am broadcast -a com.r17e.guide.DEBUG_SAY --es text "..."` sends a typed turn (emulator testing without a mic). */
    private val debugSay = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getBooleanExtra("drop", false)) { controller?.debugDropConnection(); return }
            if (i.getBooleanExtra("stop", false)) { stopSelf(); return }
            if (i.getBooleanExtra("offline_retry", false)) { controller?.retryFromOffline("debug"); return }
            i.getStringExtra("audio")?.let { controller?.injectAudio(java.io.File(getExternalFilesDir(null), it)); return }
            i.getStringExtra("text")?.let { android.util.Log.i("R17E_CTL", "debug say (controller=${controller != null})"); if (offline != null) offline?.onHeard(listOf(it)) else controller?.sendText(it) } }
    }

    private val net = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { controller?.networkAvailable() }
    }
    private var tts: android.speech.tts.TextToSpeech? = null

    /** Speak a short scam warning by itself (rate-limited by the caller). Uses the live voice when connected, else on-device TTS. */
    fun warnScam() {
        val lang = (application as R17eApp).prefs.lang
        com.r17e.guide.a11y.GuideAccessibilityService.instance?.showScamBanner()
        val c = controller
        if (c != null && c.isLive && offline == null) {
            c.sendText("[Safety notice from the phone, not from the user] The screen now shows signs of a scam " +
                "(for example KYC expiry, account block, prize, or a request for an OTP/PIN). Without waiting for a question, " +
                "in one or two short calm sentences in ${lang.promptName}, warn the user not to share any OTP or PIN, not to send money, " +
                "and to ask their family first. Do not highlight anything.")
        } else {
            val text = com.r17e.guide.i18n.S.get(lang, "scam_warn")
            val t = tts
            if (t != null) t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "scam")
            else tts = android.speech.tts.TextToSpeech(this) { ok ->
                if (ok == android.speech.tts.TextToSpeech.SUCCESS) tts?.run { language = java.util.Locale.forLanguageTag(lang.ttsTag); speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "scam") }
            }
        }
        android.util.Log.i("R17E_SAFETY", "spoken scam warning (live=${c?.isLive == true && offline == null})")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        runCatching { getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(net) }
        if (com.r17e.guide.BuildConfig.DEBUG) {
            androidx.core.content.ContextCompat.registerReceiver(this, debugSay,
                android.content.IntentFilter("com.r17e.guide.DEBUG_SAY"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startInForeground()
        if (controller == null) {
            HelperState.set(Phase.CONNECTING, null)
            val app = application as R17eApp
            app.prefs.stoppedIdle = false
            controller = app.newController(::onState) { main.post { app.prefs.stoppedIdle = true; stopSelf() } }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val lang = (application as R17eApp).prefs.lang
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Helper", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GuideSessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.r17e.guide.R.drawable.ic_mic)
            .setContentTitle(com.r17e.guide.i18n.S.get(lang, "notif_listening"))
            .setContentText(com.r17e.guide.i18n.S.get(lang, "notif_text"))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, com.r17e.guide.i18n.S.get(lang, "stop"), stop).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        instance = null
        runCatching { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(net) }
        tts?.shutdown(); tts = null
        if (com.r17e.guide.BuildConfig.DEBUG) runCatching { unregisterReceiver(debugSay) }
        controller?.stop(); controller = null
        offline?.stop(); offline = null
        _state.value = GuideController.State.STOPPED
        HelperState.set(Phase.STOPPED, null)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "r17e_helper"
        private const val NOTIF_ID = 17
        private const val ACTION_STOP = "com.r17e.guide.STOP"
        private val _state = MutableStateFlow(GuideController.State.STOPPED)
        val state: StateFlow<GuideController.State> = _state
        @Volatile var instance: GuideSessionService? = null; private set

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, GuideSessionService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GuideSessionService::class.java))
    }
}
