package com.r17e.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.r17e.guide.BuildConfig
import com.r17e.guide.R17eApp
import com.r17e.guide.a11y.A11yStatus
import com.r17e.guide.i18n.Lang
import com.r17e.guide.i18n.S
import com.r17e.guide.session.GuideSessionService
import com.r17e.guide.session.HelperState
import com.r17e.guide.session.Phase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val prefs by lazy { (application as R17eApp).prefs }
    private var permTick by mutableIntStateOf(0)
    private var familyGroup by mutableStateOf<String?>(null)
    private var screen by mutableStateOf("home")

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private val micOk get() = has(Manifest.permission.RECORD_AUDIO)
    private val notifOk get() = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)

    /** Open a system Settings page in its own task, so Back returns cleanly to our app. */
    private fun openSystem(action: String, pkgUri: Boolean = false) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkgUri) i.data = Uri.fromParts("package", packageName, null)
        runCatching { startActivity(i) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val unusedPauseOn get() = Build.VERSION.SDK_INT >= 30 && !packageManager.isAutoRevokeWhitelisted
    private val batteryRestricted get() = !getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** Live screen-helper state. Writes state only on change so the UI stays idle. */
    @Composable
    private fun rememberA11yState(): A11yStatus.State {
        val connected by A11yStatus.connected.collectAsStateWithLifecycle()
        var enabled by remember { mutableStateOf(A11yStatus.isEnabled(this)) }
        var since by remember { mutableStateOf<Long?>(null) }
        var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
        LaunchedEffect(Unit) {
            while (true) {
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

    override fun onResume() { super.onResume(); com.r17e.guide.a11y.GuideAccessibilityService.appInFront = true }
    override fun onPause() { com.r17e.guide.a11y.GuideAccessibilityService.appInFront = false; super.onPause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        familyGroup = prefs.familyGroup
        screen = savedInstanceState?.getString("screen") ?: "home"
        setContent {
            R17eTheme {
                var lang by remember { mutableStateOf(prefs.lang) }
                var onboarded by remember { mutableStateOf(prefs.onboarded) }
                val setLang: (Lang) -> Unit = { lang = it; prefs.lang = it }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        when {
                            !onboarded -> Onboarding(lang, setLang) { prefs.onboarded = true; onboarded = true }
                            screen == "settings" -> { BackHandler { screen = "home" }; SettingsScreen(lang, setLang) }
                            else -> Home(lang)
                        }
                    }
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
        @Suppress("UNUSED_VARIABLE") val tick = permTick
        val last = 6
        BackHandler(enabled = step > 0) { step-- }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = Space.screen, vertical = Space.s2)) {
                StepIndicator(step, last + 1, s("step_of").replace("{n}", "${step + 1}").replace("{m}", "${last + 1}"))
            }
            AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                (fadeIn(tween(Motion.medium)) + slideInHorizontally(tween(Motion.medium)) { if (targetState > initialState) it / 8 else -it / 8 }) togetherWith
                    fadeOut(tween(Motion.short))
            }, label = "step") { st ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.s2),
                    verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    when (st) {
                        0 -> {
                            StepHeader(Icons.Rounded.Translate, s("app"), s("tagline"))
                            Text(s("choose_lang"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            LangChoices(lang, setLang)
                        }
                        1 -> {
                            StepHeader(Icons.Rounded.RecordVoiceOver, s("how_title"), null)
                            HowRow(Icons.Rounded.Mic, s("how_1")); HowRow(Icons.Rounded.CenterFocusStrong, s("how_2")); HowRow(Icons.Rounded.PanTool, s("how_3"))
                            Banner(Tone.Info, s("privacy_title"), s("privacy"), icon = Icons.Rounded.Lock)
                        }
                        2 -> PermissionStep(Icons.Rounded.Mic, s("mic_title"), s("mic_body"), micOk, s("allow"), s("granted")) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                        3 -> PermissionStep(Icons.Rounded.NotificationsActive, s("notif_title"), s("notif_body"), notifOk, s("allow"), s("granted")) {
                            if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        4 -> {
                            StepHeader(Icons.Rounded.AccessibilityNew, s("a11y_title"), s("a11y_body"))
                            if (a11yOn) StatusPill(s("granted"), Tone.Success)
                            else {
                                PrimaryButton(s("open_settings"), icon = Icons.Rounded.OpenInNew) { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                                Banner(Tone.Info, null, s("a11y_restricted"), action = s("open_app_info")) { openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri = true) }
                            }
                        }
                        5 -> {
                            StepHeader(Icons.Rounded.Groups, s("family_title"), null)
                            FamilyPanel(lang)
                        }
                        else -> StepHeader(Icons.Rounded.CheckCircle, s("ready_title"), s("ready_body"))
                    }
                }
            }
            val canNext = when (step) { 2 -> micOk; 4 -> a11yOn; else -> true }
            val label = when {
                step == 1 -> s("agree"); step == last -> s("done")
                !canNext && step in setOf(3, 4) -> s("skip"); step == 5 && familyGroup == null -> s("skip")
                else -> s("continue")
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) QuietButton(s("back")) { step-- }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(label, Modifier.widthIn(min = 160.dp), enabled = canNext || step in setOf(3, 4)) { if (step == last) finish() else step++ }
                }
            }
        }
    }

    @Composable
    private fun StepHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String?) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            IconBadge(icon)
            Spacer(Modifier.height(Space.s1))
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            body?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    @Composable
    private fun HowRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            IconBadge(icon, size = 48.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.s1))
        }
    }

    @Composable
    private fun PermissionStep(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, ok: Boolean, allow: String, granted: String, ask: () -> Unit) {
        StepHeader(icon, title, body)
        if (ok) StatusPill(granted, Tone.Success) else PrimaryButton(allow, onClick = ask)
    }

    @Composable
    private fun LangChoices(lang: Lang, setLang: (Lang) -> Unit) {
        Column(Modifier.semantics { selectableGroup() }, verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Lang.values().forEach { l -> ChoiceRow(l.label, l == lang) { setLang(l) } }
        }
    }

    // ---------------- Family ----------------

    /** Family link: empty -> linking (progress) -> error / linked. */
    @Composable
    private fun FamilyPanel(lang: Lang) {
        val s = { k: String -> S.get(lang, k) }
        val app = application as R17eApp
        var code by rememberSaveable { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val group = familyGroup
        if (group != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {}) {
                IconBadge(Icons.Rounded.Groups, size = 56.dp, container = LocalStatus.current.successContainer, content = LocalStatus.current.onSuccessContainer)
                Column(Modifier.weight(1f)) {
                    Text(s("family_linked"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(group, style = MaterialTheme.typography.titleLarge)
                }
            }
            StatusPill(s("family_alerts_on"), Tone.Success)
            Text(s("family_alerts"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Text(s("family_body"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(20); error = null },
            label = { Text(s("family_code")) }, singleLine = true, isError = error != null, enabled = !busy,
            leadingIcon = { Icon(Icons.Rounded.Key, null) },
            supportingText = error?.let { e -> { Text(e, style = MaterialTheme.typography.bodySmall) } },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().semantics { if (error != null) liveRegion = LiveRegionMode.Assertive },
        )
        PrimaryButton(if (busy) s("family_wait") else s("family_connect"), loading = busy, enabled = code.length >= 6, icon = Icons.Rounded.Link) {
            busy = true
            scope.launch {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.backend.linkFamily(app.auth.accessToken(), code) } }
                busy = false
                r.onSuccess { name -> val g = name.ifBlank { "Family" }; prefs.familyGroup = g; familyGroup = g; app.flushSafety() }
                 .onFailure { e -> val c = (e as? com.r17e.guide.backend.BackendException)?.code; error = if (c == 404 || c == 400) s("family_bad_code") else s("family_net") }
            }
        }
        Text(s("family_alerts"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // ---------------- Home ----------------

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home(lang: Lang) {
        val s = { k: String -> S.get(lang, k) }
        val status by HelperState.status.collectAsStateWithLifecycle()
        val a11y = rememberA11yState()
        LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick
        val running = status.phase != Phase.STOPPED
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(s("app"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
                actions = {
                    IconButton(onClick = { screen = "settings" }, Modifier.size(56.dp)) { Icon(Icons.Rounded.Settings, s("settings"), Modifier.size(28.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets(0),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.s1),
                verticalArrangement = Arrangement.spacedBy(Space.s3), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusHero(status.phase, phaseTitle(lang, status.phase))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s1),
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(phaseTitle(lang, status.phase), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(phaseSub(lang, status.phase), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                status.caption?.takeIf { running && it.isNotBlank() }?.let { GuidanceCard(s("next_step"), it) }
                HelperButton(lang, running)
                if (!running && prefs.stoppedIdle) Banner(Tone.Info, null, s("stopped_idle"), icon = Icons.Rounded.Bedtime)
                if (!micOk) Banner(Tone.Error, null, s("mic_needed"), action = s("allow")) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                A11yBanner(lang, a11y)
                if (unusedPauseOn) Banner(Tone.Warning, s("unused_title"), s("unused_body"), action = s("fix_now"), icon = Icons.Rounded.EventBusy) {
                    openSystem(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri = true)
                }
                SectionCard {
                    Text(s("family_title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    FamilyPanel(lang)
                }
                SectionCard {
                    Text(s("try_saying"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    listOf("ex_1", "ex_2", "ex_3").forEach { k ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary)
                            Text(s(k).trim('"'), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.height(Space.s2))
            }
        }
    }

    @Composable
    private fun HelperButton(lang: Lang, running: Boolean) {
        val s = { k: String -> S.get(lang, k) }
        PrimaryButton(if (running) s("stop") else s("start"), Modifier.fillMaxWidth().heightIn(min = 64.dp),
            icon = if (running) Icons.Rounded.Stop else Icons.Rounded.Mic, destructive = running) {
            when {
                running -> GuideSessionService.stop(this@MainActivity)
                !micOk -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                else -> { prefs.stoppedIdle = false; GuideSessionService.start(this@MainActivity) }
            }
        }
    }

    @Composable
    private fun A11yBanner(lang: Lang, a11y: A11yStatus.State) {
        val s = { k: String -> S.get(lang, k) }
        when (a11y) {
            A11yStatus.State.ON -> {}
            A11yStatus.State.STARTING -> LoadingState(s("a11y_starting"))
            A11yStatus.State.OFF -> Banner(Tone.Warning, s("screen_helper"), s("screen_off"), action = s("turn_on"), icon = Icons.Rounded.AccessibilityNew) {
                openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            A11yStatus.State.STUCK -> Banner(Tone.Error, s("screen_helper"), s("a11y_stuck"), action = s("open_settings")) {
                openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        }
    }

    // ---------------- Settings ----------------

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(lang: Lang, setLang: (Lang) -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        val status by HelperState.status.collectAsStateWithLifecycle()
        val a11y = rememberA11yState()
        LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick
        val app = application as R17eApp
        val running = status.phase != Phase.STOPPED
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(s("settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
                navigationIcon = { IconButton(onClick = { screen = "home" }, Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, s("back"), Modifier.size(28.dp)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets(0),
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.s2, vertical = Space.s1),
                verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                SectionHeader(s("helper_section"))
                SectionCard {
                    ListRow(if (running) Icons.Rounded.Mic else Icons.Rounded.MicOff, s("helper_section"), phaseTitle(lang, status.phase),
                        trailing = {
                            Switch(checked = running, onCheckedChange = { on ->
                                if (!on) GuideSessionService.stop(this@MainActivity)
                                else if (!micOk) askMic.launch(Manifest.permission.RECORD_AUDIO)
                                else { prefs.stoppedIdle = false; GuideSessionService.start(this@MainActivity) }
                            }, modifier = Modifier.semantics { contentDescription = s("helper_section") })
                        })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(Icons.Rounded.AccessibilityNew, s("screen_helper"),
                        if (a11y == A11yStatus.State.ON) s("on") else if (a11y == A11yStatus.State.STARTING) s("a11y_starting") else s("off"),
                        onClick = { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
                    if (a11y == A11yStatus.State.STUCK) Banner(Tone.Error, null, s("a11y_stuck"))
                    if (unusedPauseOn) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListRow(Icons.Rounded.EventBusy, s("unused_title"), s("unused_body"), onClick = { openSystem(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri = true) }) }
                    if (batteryRestricted) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListRow(Icons.Rounded.BatteryAlert, s("battery_title"), s("battery_body"), onClick = { openSystem(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) }
                }
                SectionHeader(s("language"))
                SectionCard { LangChoices(lang, setLang) }
                SectionHeader(s("family_title"))
                SectionCard { FamilyPanel(lang) }
                SectionHeader(s("privacy_title"))
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s("privacy"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionHeader(s("about"))
                SectionCard {
                    ListRow(Icons.Rounded.Info, s("version"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", trailing = null)
                    ListRow(Icons.Rounded.Dns, s("server"), BuildConfig.BACKEND_URL.substringAfter("//").substringBefore('.'), trailing = null)
                    app.auth.deviceId?.let { ListRow(Icons.Rounded.Smartphone, s("device_id"), it.take(8), trailing = null) }
                    ListRow(Icons.Rounded.Contrast, s("appearance"), s("theme_note"), trailing = null)
                }
                Spacer(Modifier.height(Space.s3))
            }
        }
    }

    companion object {
        /** One-line status, used by the bubble and the notification. */
        fun phaseText(lang: Lang, p: Phase) = S.get(lang, when (p) {
            Phase.STOPPED -> "st_stopped"; Phase.CONNECTING -> "st_connecting"; Phase.LISTENING -> "st_listening"
            Phase.SPEAKING -> "st_speaking"; Phase.RECONNECTING -> "st_reconnecting"; Phase.OFFLINE -> "st_offline"
        })
        fun phaseTitle(lang: Lang, p: Phase) = S.get(lang, when (p) {
            Phase.STOPPED -> "st_stopped"; Phase.CONNECTING -> "st_connecting_t"; Phase.LISTENING -> "st_listening_t"
            Phase.SPEAKING -> "st_speaking_t"; Phase.RECONNECTING -> "st_reconnecting_t"; Phase.OFFLINE -> "st_offline_t"
        })
        fun phaseSub(lang: Lang, p: Phase) = S.get(lang, when (p) {
            Phase.STOPPED -> "st_stopped_sub"; Phase.CONNECTING -> "st_connecting_sub"; Phase.LISTENING -> "st_listening_sub"
            Phase.SPEAKING -> "st_speaking_sub"; Phase.RECONNECTING -> "st_reconnecting_sub"; Phase.OFFLINE -> "st_offline_sub"
        })
    }
}
