package com.r17e.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.r17e.guide.BuildConfig
import com.r17e.guide.R17eApp
import com.r17e.guide.a11y.A11yStatus
import com.r17e.guide.i18n.Lang
import com.r17e.guide.i18n.S
import com.r17e.guide.session.GuideSessionService
import com.r17e.guide.session.HelperState
import com.r17e.guide.session.Phase

class MainActivity : ComponentActivity() {
    private val prefs by lazy { (application as R17eApp).prefs }
    private var permTick by mutableIntStateOf(0)
    private var familyGroup by mutableStateOf<String?>(null)
    private var screen by mutableStateOf("home")

    /** Open a system Settings page in its own task, so Back returns cleanly to our app. */
    private fun openSystem(action: String, pkgUri: Boolean = false) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkgUri) i.data = Uri.fromParts("package", packageName, null)
        runCatching { startActivity(i) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** "Pause app activity if unused" is still on (Android 11+). */
    private val unusedPauseOn get() = Build.VERSION.SDK_INT >= 30 && !packageManager.isAutoRevokeWhitelisted
    /** Battery optimisation may stop a long session. */
    private val batteryRestricted get() = !getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** Live screen-helper state; polls the setting once a second while visible (cheap), plus the bind flow. */
    @Composable
    private fun rememberA11yState(): A11yStatus.State {
        val connected by A11yStatus.connected.collectAsStateWithLifecycle()
        var enabled by remember { mutableStateOf(A11yStatus.isEnabled(this)) }
        var since by remember { mutableStateOf<Long?>(null) }
        var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
        LaunchedEffect(Unit) {
            while (true) {
                // Only write state when something changes, so the UI stays idle (no per-second recomposition).
                val e = A11yStatus.isEnabled(this@MainActivity)
                if (e != enabled) enabled = e
                val waiting = e && !A11yStatus.connected.value
                if (waiting) { val t = android.os.SystemClock.elapsedRealtime(); if (since == null) since = t; if (t - since!! <= A11yStatus.GRACE_MS + 1000) now = t }
                else if (since != null) since = null
                kotlinx.coroutines.delay(1000)
            }
        }
        return A11yStatus.state(enabled, connected, since, now)
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private val micOk get() = has(Manifest.permission.RECORD_AUDIO)
    private val notifOk get() = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        familyGroup = prefs.familyGroup
        screen = savedInstanceState?.getString("screen") ?: "home"
        setContent {
            R17eTheme {
                var lang by remember { mutableStateOf(prefs.lang) }
                var onboarded by remember { mutableStateOf(prefs.onboarded) }
                val setLang: (Lang) -> Unit = { lang = it; prefs.lang = it }
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!onboarded) Onboarding(lang, setLang) { prefs.onboarded = true; onboarded = true }
                    else if (screen == "settings") {
                        androidx.activity.compose.BackHandler { screen = "home" }
                        SettingsScreen(lang, setLang)
                    } else Home(lang, setLang)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("screen", screen) }

    // ---------------- Onboarding ----------------

    @Composable
    private fun Onboarding(lang: Lang, setLang: (Lang) -> Unit, finish: () -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        var step by rememberSaveable { mutableIntStateOf(0) }
        val a11yOn = rememberA11yState() != A11yStatus.State.OFF
        LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick // recompose after permission results
        val last = 6
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / (last + 1f) }, Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "${step + 1} / ${last + 1}" })
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (step) {
                    0 -> {
                        Text(s("app"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(s("tagline"), style = MaterialTheme.typography.bodyLarge)
                        Text(s("choose_lang"), style = MaterialTheme.typography.headlineSmall)
                        LangPicker(lang, setLang)
                    }
                    1 -> {
                        Text(s("how_title"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        listOf("how_1", "how_2", "how_3").forEachIndexed { i, k -> Bullet("${i + 1}", s(k)) }
                        InfoCard(s("privacy"))
                    }
                    2 -> PermissionStep(s("mic_title"), s("mic_body"), micOk, s("allow"), s("granted")) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                    3 -> PermissionStep(s("notif_title"), s("notif_body"), notifOk, s("allow"), s("granted")) {
                        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    4 -> {
                        Text(s("a11y_title"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(s("a11y_body"), style = MaterialTheme.typography.bodyLarge)
                        if (a11yOn) Text(s("granted"), color = R17eColors.Listening, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        else {
                            BigButton(s("open_settings")) { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                            InfoCard(s("a11y_restricted"))
                            OutlinedButton(onClick = { openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri = true) },
                                Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(s("open_app_info"), fontSize = 20.sp) }
                        }
                    }
                    5 -> {
                        Text(s("family_title"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        FamilyLink(lang)
                    }
                    else -> {
                        Text(s("ready_title"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(s("ready_body"), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, Modifier.weight(1f).heightIn(min = 64.dp)) { Text(s("back"), fontSize = 20.sp) }
                val canNext = when (step) { 2 -> micOk; 4 -> a11yOn; else -> true }
                val label = when { step == 1 -> s("agree"); step == last -> s("done"); !canNext && step in setOf(3, 4) -> s("skip"); step == 5 && familyGroup == null -> s("skip"); else -> s("next") }
                Button(
                    onClick = { if (step == last) finish() else step++ },
                    enabled = canNext || step in setOf(3, 4),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) { Text(label, fontSize = 22.sp) }
            }
        }
    }

    @Composable
    private fun FamilyLink(lang: Lang) {
        val s = { k: String -> S.get(lang, k) }
        val app = application as R17eApp
        val group = familyGroup
        var code by rememberSaveable { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        if (group != null) {
            Text("${s("family_linked")}: $group ✓", color = R17eColors.Listening, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(s("family_alerts"), fontSize = 18.sp, color = R17eColors.Ink)
            return
        }
        Text(s("family_body"), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(20); error = null },
            label = { Text(s("family_code"), fontSize = 18.sp) }, singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 30.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = R17eColors.Danger, fontSize = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 4.dp)
            Text(s("family_wait"), fontSize = 20.sp, color = R17eColors.Ink)
        }
        Button(
            enabled = code.length >= 6 && !busy,
            onClick = {
                busy = true
                scope.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { app.backend.linkFamily(app.auth.accessToken(), code) }
                    }
                    busy = false
                    r.onSuccess { name -> val g = name.ifBlank { "Family" }; prefs.familyGroup = g; familyGroup = g; app.flushSafety() }
                     .onFailure { e -> error = if ((e as? com.r17e.guide.backend.BackendException)?.code == 404 || (e as? com.r17e.guide.backend.BackendException)?.code == 400) s("family_bad_code") else s("family_net") }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(s("family_connect"), fontSize = 22.sp) }
        Text(s("family_alerts"), fontSize = 18.sp, color = R17eColors.Ink)
    }

    @Composable
    private fun PermissionStep(title: String, body: String, ok: Boolean, allow: String, granted: String, ask: () -> Unit) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (ok) Text(granted, color = R17eColors.Listening, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        else BigButton(allow, onClick = ask)
    }

    // ---------------- Home ----------------

    @Composable
    private fun Home(lang: Lang, setLang: (Lang) -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        val status by HelperState.status.collectAsStateWithLifecycle()
        val a11y = rememberA11yState()
        LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick
        val running = status.phase != Phase.STOPPED
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s("app"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
                OutlinedButton(onClick = { screen = "settings" }, Modifier.heightIn(min = 56.dp)) { Text("⚙ " + s("settings"), fontSize = 18.sp) }
            }
            StatusOrb(status.phase, phaseText(lang, status.phase))
            Text(
                phaseText(lang, status.phase), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            status.caption?.takeIf { running && it.isNotBlank() }?.let {
                Text("“$it”", fontSize = 24.sp, color = R17eColors.Warn, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
            HelperButton(lang, running)
            if (!running && prefs.stoppedIdle) Text(s("stopped_idle"), fontSize = 20.sp, color = R17eColors.Ink, textAlign = TextAlign.Center)
            if (!micOk) Text(s("mic_needed"), color = R17eColors.Danger, fontSize = 20.sp)
            A11yCard(lang, a11y)
            if (unusedPauseOn) FixCard(s("unused_title"), s("unused_body"), s("fix_now")) {
                openSystem(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri = true)
            }
            Card(colors = CardDefaults.cardColors(containerColor = R17eColors.Card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s("family_title"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    FamilyLink(lang)
                }
            }
            InfoCard(s("try_saying") + ":\n" + listOf("ex_1", "ex_2", "ex_3").joinToString("\n") { "• " + s(it) })
        }
    }

    @Composable
    private fun HelperButton(lang: Lang, running: Boolean) {
        val s = { k: String -> S.get(lang, k) }
        BigButton(if (running) s("stop") else s("start"), danger = running) {
            when {
                running -> GuideSessionService.stop(this@MainActivity)
                !micOk -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                else -> { prefs.stoppedIdle = false; GuideSessionService.start(this@MainActivity) }
            }
        }
    }

    @Composable
    private fun A11yCard(lang: Lang, a11y: A11yStatus.State) {
        val s = { k: String -> S.get(lang, k) }
        when (a11y) {
            A11yStatus.State.ON -> {}
            A11yStatus.State.STARTING -> Text(s("a11y_starting"), fontSize = 18.sp, color = R17eColors.Ink)
            A11yStatus.State.OFF -> FixCard(null, s("screen_off"), s("turn_on")) { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            A11yStatus.State.STUCK -> FixCard(null, s("a11y_stuck"), s("open_settings")) { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        }
    }

    @Composable
    private fun FixCard(title: String?, body: String, button: String, onClick: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                title?.let { Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = R17eColors.Ink, modifier = Modifier.semantics { heading() }) }
                Text(body, fontSize = 20.sp, lineHeight = 28.sp, color = R17eColors.Ink)
                Button(onClick = onClick, Modifier.heightIn(min = 56.dp)) { Text(button, fontSize = 20.sp) }
            }
        }
    }

    // ---------------- Settings ----------------

    @Composable
    private fun SettingsScreen(lang: Lang, setLang: (Lang) -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        val status by HelperState.status.collectAsStateWithLifecycle()
        val a11y = rememberA11yState()
        LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick
        val app = application as R17eApp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { screen = "home" }, Modifier.heightIn(min = 56.dp)) { Text("← " + s("back"), fontSize = 18.sp) }
                Text(s("settings"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
            Section(s("helper_section")) {
                Text(phaseText(lang, status.phase), fontSize = 20.sp, color = R17eColors.Ink)
                HelperButton(lang, status.phase != Phase.STOPPED)
                Text(s("screen_helper") + ": " + if (a11y == A11yStatus.State.ON) s("on") else "—", fontSize = 20.sp, color = R17eColors.Ink)
                A11yCard(lang, a11y)
                if (unusedPauseOn) FixCard(s("unused_title"), s("unused_body"), s("fix_now")) { openSystem(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri = true) }
                if (batteryRestricted) FixCard(s("battery_title"), s("battery_body"), s("fix_now")) { openSystem(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            }
            Section(s("language")) { LangPicker(lang, setLang) }
            Section(s("family_title")) { FamilyLink(lang) }
            Section(s("about")) {
                Text("${s("version")}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 18.sp, color = R17eColors.Ink)
                Text("${s("server")}: ${BuildConfig.BACKEND_URL.substringAfter("//").substringBefore('.')}", fontSize = 18.sp, color = R17eColors.Ink)
                app.auth.deviceId?.let { Text("ID: ${it.take(8)}", fontSize = 18.sp, color = R17eColors.Ink) }
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = R17eColors.Card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                content()
            }
        }
    }

    @Composable
    private fun StatusOrb(phase: Phase, label: String) {
        val color = when (phase) {
            Phase.LISTENING -> R17eColors.Listening; Phase.SPEAKING -> R17eColors.Speaking
            Phase.CONNECTING, Phase.RECONNECTING -> R17eColors.Warn; Phase.OFFLINE -> R17eColors.Danger; Phase.STOPPED -> Color(0xFF9E9E9E)
        }
        val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
            1f, if (phase == Phase.LISTENING || phase == Phase.SPEAKING) 1.12f else 1f,
            infiniteRepeatable(tween(if (phase == Phase.SPEAKING) 450 else 900), RepeatMode.Reverse), label = "s",
        )
        Box(
            Modifier.size(150.dp).scale(pulse).clearAndSetSemantics { contentDescription = label }.background(color.copy(alpha = 0.15f), CircleShape).border(6.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(when (phase) { Phase.SPEAKING -> "🔊"; Phase.LISTENING -> "🎤"; Phase.OFFLINE -> "📴"; Phase.STOPPED -> "▶"; else -> "…" }, fontSize = 56.sp)
        }
    }

    // ---------------- Shared bits ----------------

    @Composable
    private fun LangPicker(lang: Lang, setLang: (Lang) -> Unit) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Lang.values().forEach { l ->
                val sel = l == lang
                OutlinedButton(
                    onClick = { setLang(l) },
                    colors = if (sel) ButtonDefaults.outlinedButtonColors(containerColor = R17eColors.Primary, contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { selected = sel; role = Role.RadioButton },
                ) { Text((if (sel) "✓ " else "") + l.label, fontSize = 22.sp) }
            }
        }
    }

    @Composable
    private fun BigButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = if (danger) ButtonDefaults.buttonColors(containerColor = R17eColors.Danger) else ButtonDefaults.buttonColors(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        ) { Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
    }

    @Composable
    private fun Bullet(n: String, text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Box(Modifier.size(40.dp).background(R17eColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(n, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    private fun InfoCard(text: String) {
        Card(colors = CardDefaults.cardColors(containerColor = R17eColors.Card), modifier = Modifier.fillMaxWidth()) {
            Text(text, Modifier.padding(18.dp), fontSize = 20.sp, lineHeight = 28.sp, color = R17eColors.Ink)
        }
    }

    companion object {
        fun phaseText(lang: Lang, p: Phase) = S.get(lang, when (p) {
            Phase.STOPPED -> "st_stopped"; Phase.CONNECTING -> "st_connecting"; Phase.LISTENING -> "st_listening"
            Phase.SPEAKING -> "st_speaking"; Phase.RECONNECTING -> "st_reconnecting"; Phase.OFFLINE -> "st_offline"
        })
    }
}
