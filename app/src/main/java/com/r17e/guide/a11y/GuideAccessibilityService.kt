package com.r17e.guide.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.r17e.guide.overlay.HelperBubble
import com.r17e.guide.session.GuideSessionService
import com.r17e.guide.session.HelperState
import com.r17e.guide.session.Phase
import com.r17e.guide.i18n.S
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import com.r17e.guide.overlay.HighlightOverlay

/**
 * Reads the screen and points at buttons. GUIDE-ONLY: this service never calls
 * performAction or dispatchGesture.
 */
class GuideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val snapshotRunnable = Runnable { takeSnapshot() }
    private var lastScreen = "?"
    private var lastJson: String? = null

    lateinit var highlight: HighlightOverlay; private set
    private lateinit var bubble: HelperBubble
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile var current: Snapshot? = null; private set

    override fun onServiceConnected() {
        instance = this
        highlight = HighlightOverlay(this)
        bubble = HelperBubble(this, highlight, { current }) {
            if (HelperState.status.value.phase == Phase.STOPPED) GuideSessionService.start(this) else GuideSessionService.stop(this)
        }
        bubble.show()
        scope.launch {
            HelperState.status.collectLatest { st ->
                val lang = (application as com.r17e.guide.R17eApp).prefs.lang
                bubble.render(st.phase, com.r17e.guide.ui.MainActivity.phaseText(lang, st.phase) + ". " + S.get(lang, "bubble_hint"))
                if (st.phase == Phase.STOPPED) highlight.hide()
            }
        }
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return // ignore our own overlays
        handler.removeCallbacks(snapshotRunnable)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.let { lastScreen = it.substringAfterLast('.') }
            handler.post(snapshotRunnable)
        } else {
            handler.postDelayed(snapshotRunnable, DEBOUNCE_MS)
        }
    }

    private fun takeSnapshot() {
        val root = appRoot() ?: return
        val dm = resources.displayMetrics
        val snap = SnapshotBuilder.build(root, lastScreen, dm.widthPixels, dm.heightPixels)
        if (snap.pkg == packageName) return
        val json = snap.toJson()
        current = snap
        (application as com.r17e.guide.R17eApp).onSafetySnapshot(snap)
        highlight.onSnapshot(snap)
        bubble.onSnapshot(snap)
        if (json != lastJson) {
            lastJson = json
            json.chunked(3500).forEach { Log.d(SNAP_TAG, it) }
        }
    }

    /** Root of the active app window, skipping our own overlay windows. */
    private fun appRoot() = try {
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isActive || it.isFocused }
            .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() != packageName } }
            ?: rootInActiveWindow
    } catch (e: Exception) { rootInActiveWindow }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (::bubble.isInitialized) bubble.hide()
        if (::highlight.isInitialized) highlight.hide()
        instance = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "R17E"
        const val SNAP_TAG = "R17E_SNAP"
        const val DEBOUNCE_MS = 150L
        @Volatile var instance: GuideAccessibilityService? = null
    }
}
