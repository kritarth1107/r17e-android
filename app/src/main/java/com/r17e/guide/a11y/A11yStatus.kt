package com.r17e.guide.a11y

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Whether the user has switched our accessibility service on, read from system settings.
 * The old check (service instance != null) said "off" for a moment after every reinstall/restart while the
 * system was still re-binding the service; the setting is the source of truth.
 */
object A11yStatus {
    fun isEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, GuideAccessibilityService::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
