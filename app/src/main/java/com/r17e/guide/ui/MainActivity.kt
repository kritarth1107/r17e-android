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

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private val micOk get() = has(Manifest.permission.RECORD_AUDIO)
    private val notifOk get() = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            R17eTheme {
                var lang by remember { mutableStateOf(prefs.lang) }
                var onboarded by remember { mutableStateOf(prefs.onboarded) }
                val setLang: (Lang) -> Unit = { lang = it; prefs.lang = it }
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!onboarded) Onboarding(lang, setLang) { prefs.onboarded = true; onboarded = true }
                    else Home(lang, setLang)
                }
            }
        }
    }

    // ---------------- Onboarding ----------------

    @Composable
    private fun Onboarding(lang: Lang, setLang: (Lang) -> Unit, finish: () -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        var step by rememberSaveable { mutableIntStateOf(0) }
        var a11yOn by remember { mutableStateOf(A11yStatus.isEnabled(this)) }
        LifecycleResumeEffect(Unit) { a11yOn = A11yStatus.isEnabled(this@MainActivity); permTick++; onPauseOrDispose { } }
        @Suppress("UNUSED_VARIABLE") val tick = permTick // recompose after permission results
        val last = 6
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / (last + 1f) }, Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "Step ${step + 1} of ${last + 1}" })
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (step) {
                    0 -> {
                        Text(s("app"), style = MaterialTheme.typography.headlineLarge)
                        Text(s("tagline"), style = MaterialTheme.typography.bodyLarge)
                        Text(s("choose_lang"), style = MaterialTheme.typography.headlineSmall)
                        LangPicker(lang, setLang)
                    }
                    1 -> {
                        Text(s("how_title"), style = MaterialTheme.typography.headlineLarge)
                        listOf("how_1", "how_2", "how_3").forEachIndexed { i, k -> Bullet("${i + 1}", s(k)) }
                        InfoCard(s("privacy"))
                    }
                    2 -> PermissionStep(s("mic_title"), s("mic_body"), micOk, s("allow"), s("granted")) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                    3 -> PermissionStep(s("notif_title"), s("notif_body"), notifOk, s("allow"), s("granted")) {
                        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    4 -> {
                        Text(s("a11y_title"), style = MaterialTheme.typography.headlineLarge)
                        Text(s("a11y_body"), style = MaterialTheme.typography.bodyLarge)
                        if (a11yOn) Text(s("granted"), color = R17eColors.Listening, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        else {
                            BigButton(s("open_settings")) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            InfoCard(s("a11y_restricted"))
                            OutlinedButton(onClick = {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                            }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(s("open_app_info"), fontSize = 20.sp) }
                        }
                    }
                    5 -> {
                        Text(s("family_title"), style = MaterialTheme.typography.headlineLarge)
                        FamilyLink(lang)
                    }
                    else -> {
                        Text(s("ready_title"), style = MaterialTheme.typography.headlineLarge)
                        Text(s("ready_body"), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, Modifier.weight(1f).heightIn(min = 64.dp)) { Text(s("back"), fontSize = 20.sp) }
                val canNext = when (step) { 2 -> micOk; 4 -> a11yOn; else -> true }
                val label = when { step == 1 -> s("agree"); step == last -> s("done"); !canNext && step in setOf(3, 4) -> s("skip"); step == 5 && prefs.familyGroup == null -> s("skip"); else -> s("next") }
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
        var group by remember { mutableStateOf(prefs.familyGroup) }
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
        error?.let { Text(it, color = R17eColors.Danger, fontSize = 20.sp) }
        Button(
            enabled = code.length >= 6 && !busy,
            onClick = {
                busy = true
                scope.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { app.backend.linkFamily(app.auth.accessToken(), code) }
                    }
                    busy = false
                    r.onSuccess { name -> val g = name.ifBlank { "Family" }; prefs.familyGroup = g; group = g; app.flushSafety() }
                     .onFailure { e -> error = if ((e as? com.r17e.guide.backend.BackendException)?.code == 404 || (e as? com.r17e.guide.backend.BackendException)?.code == 400) s("family_bad_code") else s("family_net") }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(if (busy) "…" else s("family_connect"), fontSize = 22.sp) }
        Text(s("family_alerts"), fontSize = 18.sp, color = R17eColors.Ink)
    }

    @Composable
    private fun PermissionStep(title: String, body: String, ok: Boolean, allow: String, granted: String, ask: () -> Unit) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (ok) Text(granted, color = R17eColors.Listening, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        else BigButton(allow, onClick = ask)
    }

    // ---------------- Home ----------------

    @Composable
    private fun Home(lang: Lang, setLang: (Lang) -> Unit) {
        val s = { k: String -> S.get(lang, k) }
        val status by HelperState.status.collectAsStateWithLifecycle()
        var a11yOn by remember { mutableStateOf(A11yStatus.isEnabled(this)) }
        LifecycleResumeEffect(Unit) { a11yOn = A11yStatus.isEnabled(this@MainActivity); permTick++; onPauseOrDispose { } }
        val running = status.phase != Phase.STOPPED
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s("app"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
            StatusOrb(status.phase)
            Text(
                phaseText(lang, status.phase), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            status.caption?.takeIf { running && it.isNotBlank() }?.let {
                Text("“$it”", fontSize = 24.sp, color = R17eColors.Warn, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
            BigButton(if (running) s("stop") else s("start"), danger = running) {
                when {
                    running -> GuideSessionService.stop(this@MainActivity)
                    !micOk -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    else -> GuideSessionService.start(this@MainActivity)
                }
            }
            if (!micOk) Text(s("mic_needed"), color = R17eColors.Danger, fontSize = 20.sp)
            if (!a11yOn) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s("screen_off"), fontSize = 20.sp, color = R17eColors.Ink)
                    Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, Modifier.heightIn(min = 56.dp)) { Text(s("turn_on"), fontSize = 20.sp) }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = R17eColors.Card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s("family_title"), style = MaterialTheme.typography.headlineSmall)
                    FamilyLink(lang)
                }
            }
            InfoCard(s("try_saying") + ":\n" + listOf("ex_1", "ex_2", "ex_3").joinToString("\n") { "• " + s(it) })
            Text(s("language"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            LangPicker(lang, setLang)
            if (BuildConfig.DEBUG) Text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.BACKEND_URL.substringAfter("//").take(40)}", fontSize = 14.sp, color = Color(0xFF555555))
        }
    }

    @Composable
    private fun StatusOrb(phase: Phase) {
        val color = when (phase) {
            Phase.LISTENING -> R17eColors.Listening; Phase.SPEAKING -> R17eColors.Speaking
            Phase.CONNECTING, Phase.RECONNECTING -> R17eColors.Warn; Phase.OFFLINE -> R17eColors.Danger; Phase.STOPPED -> Color(0xFF9E9E9E)
        }
        val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
            1f, if (phase == Phase.LISTENING || phase == Phase.SPEAKING) 1.12f else 1f,
            infiniteRepeatable(tween(if (phase == Phase.SPEAKING) 450 else 900), RepeatMode.Reverse), label = "s",
        )
        Box(
            Modifier.size(150.dp).scale(pulse).background(color.copy(alpha = 0.15f), CircleShape).border(6.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(when (phase) { Phase.SPEAKING -> "🔊"; Phase.LISTENING -> "🎤"; Phase.OFFLINE -> "📴"; Phase.STOPPED -> "⏻"; else -> "…" }, fontSize = 56.sp)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { selected = sel },
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
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
