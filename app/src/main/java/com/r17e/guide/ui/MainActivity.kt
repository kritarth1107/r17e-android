package com.r17e.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.r17e.guide.session.GuideController
import com.r17e.guide.session.GuideSessionService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.r17e.guide.a11y.GuideAccessibilityService

class MainActivity : ComponentActivity() {
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) GuideSessionService.start(this)
    }

    private fun startHelper() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) GuideSessionService.start(this) else askPerms.launch(needed.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { DisclosureScreen() } } }
    }

    @Composable
    private fun DisclosureScreen() {
        var enabled by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            enabled = GuideAccessibilityService.instance != null
            onPauseOrDispose { }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("R17E Guide", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("How this helper works", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "To help you, this app needs to see what is on your screen. It reads the buttons " +
                    "and words on the screen so it can draw a circle around the right button and tell you what to do.",
                fontSize = 20.sp,
            )
            Text("It only shows you. It never taps, types or changes anything for you.", fontSize = 20.sp)
            Text(
                "It never reads passwords. When you start the helper, your voice and the words on your screen " +
                    "are sent to Google Gemini so it can answer you. Stop the helper any time.",
                fontSize = 20.sp,
            )
            Text(
                "To turn it on: tap the button below, choose \"R17E Guide\", and switch it on.",
                fontSize = 20.sp,
            )
            Button(
                onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text("Open Accessibility settings", fontSize = 20.sp) }
            Text(
                if (enabled) "Helper is ON. Look for the blue ? bubble." else "Helper is OFF.",
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            val state by GuideSessionService.state.collectAsStateWithLifecycle()
            val running = state != GuideController.State.STOPPED
            Button(
                onClick = { if (running) GuideSessionService.stop(this@MainActivity) else startHelper() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text(if (running) "Stop helper" else "Start talking to helper", fontSize = 22.sp) }
            Text(
                when (state) {
                    GuideController.State.CONNECTING -> "Connecting..."
                    GuideController.State.LISTENING -> "I am listening. Tell me what you want to do."
                    GuideController.State.OFFLINE -> "No internet helper right now. Using simple offline help."
                    GuideController.State.STOPPED -> ""
                },
                fontSize = 20.sp,
            )
        }
    }
}
