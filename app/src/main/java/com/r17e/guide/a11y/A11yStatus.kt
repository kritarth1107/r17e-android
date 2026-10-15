package com.r17e.guide.a11y

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the screen helper is really working.
 * - The setting (ENABLED_ACCESSIBILITY_SERVICES) says whether the user switched it on.
 * - [connected] says whether Android actually bound our service. After an adb reinstall, a reboot or the
 *   process being killed there is a gap, and sometimes Android never re-binds ("stuck"); then the user must
 *   toggle it. We only show "off" when the setting is off, and "stuck" after a grace period.
 */
object A11yStatus {
    enum class State { OFF, STARTING, ON, STUCK }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    internal fun setConnected(v: Boolean) { _connected.value = v }

    const val GRACE_MS = 6_000L

    fun isEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, GuideAccessibilityService::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Pure decision, unit-tested. [enabledSeenSinceMs]: when we first saw enabled-but-not-connected. */
    fun state(enabled: Boolean, connected: Boolean, enabledSeenSinceMs: Long?, nowMs: Long): State = when {
        connected -> State.ON
        !enabled -> State.OFF
        enabledSeenSinceMs != null && nowMs - enabledSeenSinceMs >= GRACE_MS -> State.STUCK
        else -> State.STARTING
    }
}
