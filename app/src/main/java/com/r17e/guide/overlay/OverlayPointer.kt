package com.r17e.guide.overlay

import android.os.Handler
import android.os.Looper
import com.r17e.guide.a11y.GuideAccessibilityService
import com.r17e.guide.a11y.Snapshot
import com.r17e.guide.session.Pointer

/** Routes model highlight requests to the accessibility overlay on the main thread. */
object OverlayPointer : Pointer {
    private val main = Handler(Looper.getMainLooper())
    override fun point(snap: Snapshot, nodeId: String, caption: String) {
        main.post { GuideAccessibilityService.instance?.highlight?.highlight(snap, nodeId, caption) }
    }
    override fun clear() { main.post { GuideAccessibilityService.instance?.highlight?.hide() } }
}
