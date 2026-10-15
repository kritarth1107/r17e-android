package com.r17e.guide.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

/**
 * A calm, clearly-worded scam warning card at the top of the screen, shown with the spoken warning.
 * Guide-only: it never touches the other app; "OK" only closes this card. Auto-hides after [AUTO_HIDE_MS].
 */
class ScamBanner(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val d = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(title: String, body: String, ok: String) {
        hide()
        val pal = OverlayPalette(ctx)
        val pad = (20 * d).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, (12 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 24 * d; setColor(pal.errorContainer) }
            elevation = 10 * d
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(ctx).apply {
            setImageResource(com.r17e.guide.R.drawable.ic_warning); imageTintList = android.content.res.ColorStateList.valueOf(pal.error)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt()).apply { marginEnd = (12 * d).toInt() })
        head.addView(TextView(ctx).apply {
            text = title; textSize = 22f; setTextColor(pal.onErrorContainer); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isFocusable = true; contentDescription = title
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        root.addView(head)
        root.addView(TextView(ctx).apply {
            text = body; textSize = 18f; setTextColor(pal.onErrorContainer); setLineSpacing(4 * d, 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * d).toInt() })
        root.addView(Button(ctx, null, android.R.attr.borderlessButtonStyle).apply {
            text = ok; textSize = 18f; setTextColor(pal.onErrorContainer); isAllCaps = false; minHeight = (48 * d).toInt(); minWidth = (96 * d).toInt()
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = (4 * d).toInt() })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; x = 0; y = (40 * d).toInt(); horizontalMargin = 0f }
        val frame = FrameLayout(ctx).apply { setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0); addView(root) }
        runCatching { wm.addView(frame, lp); view = frame; root.alpha = 0f; root.translationY = -24 * d; root.animate().alpha(1f).translationY(0f).setDuration(220).start() }
        main.postDelayed({ hide() }, AUTO_HIDE_MS)
    }

    fun hide() {
        main.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeView(it) } }; view = null
    }

    companion object { const val AUTO_HIDE_MS = 25_000L }
}
