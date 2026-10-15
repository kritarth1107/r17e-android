package com.r17e.guide.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r17e.guide.session.Phase

/** Filled primary action; 56dp+ tall, full width by default. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), icon: ImageVector? = null, enabled: Boolean = true,
                  loading: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer) else ButtonDefaults.buttonColors()
    Button(onClick = onClick, enabled = enabled && !loading, colors = colors, shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Space.s3, vertical = Space.s2),
        modifier = modifier.heightIn(min = Space.touch)) {
        if (loading) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current); Spacer(Modifier.width(Space.s2)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(Space.s1 + Space.xs)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = modifier.heightIn(min = Space.touch),
        contentPadding = PaddingValues(horizontal = Space.s3, vertical = Space.s2)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(Space.s1)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Space.touch)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Tonal icon in a soft circle; used as the hero of onboarding steps and empty states. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 88.dp, container: Color = MaterialTheme.colorScheme.primaryContainer,
              content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(Modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.46f), tint = content)
    }
}

/** Surface card with consistent padding. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2), content = content)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Space.s1, top = Space.s2, bottom = Space.xs).semantics { heading() })
}

enum class Tone { Info, Warning, Error, Success }

/** Attention banner: icon + title + body + optional action. Used for warnings, errors and tips. */
@Composable
fun Banner(tone: Tone, title: String?, body: String, action: String? = null, icon: ImageVector? = null, onAction: () -> Unit = {}) {
    val st = LocalStatus.current; val cs = MaterialTheme.colorScheme
    val (bg, fg, ic) = when (tone) {
        Tone.Info -> Triple(cs.primaryContainer, cs.onPrimaryContainer, icon ?: Icons.Rounded.Info)
        Tone.Warning -> Triple(st.warningContainer, st.onWarningContainer, icon ?: Icons.Rounded.WarningAmber)
        Tone.Error -> Triple(cs.errorContainer, cs.onErrorContainer, icon ?: Icons.Rounded.ErrorOutline)
        Tone.Success -> Triple(st.successContainer, st.onSuccessContainer, icon ?: Icons.Rounded.CheckCircle)
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.s2 + Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Icon(ic, null, Modifier.size(28.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = fg.copy(alpha = 0.12f), contentColor = fg)) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A settings-style row: leading icon, title, supporting text, trailing slot. 64dp+ tall. */
@Composable
fun ListRow(icon: ImageVector, title: String, supporting: String? = null, onClick: (() -> Unit)? = null,
            trailing: @Composable (() -> Unit)? = if (onClick != null) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(horizontal = Space.s2, vertical = Space.s1 + Space.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

/** Single-choice row with a radio button; the whole row is the 64dp target. */
@Composable
fun ChoiceRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}

/** Small pill with icon: "Allowed", "On", etc. */
@Composable
fun StatusPill(text: String, tone: Tone) {
    val st = LocalStatus.current; val cs = MaterialTheme.colorScheme
    val (bg, fg, ic) = when (tone) {
        Tone.Success -> Triple(st.successContainer, st.onSuccessContainer, Icons.Rounded.CheckCircle)
        Tone.Warning -> Triple(st.warningContainer, st.onWarningContainer, Icons.Rounded.WarningAmber)
        Tone.Error -> Triple(cs.errorContainer, cs.onErrorContainer, Icons.Rounded.ErrorOutline)
        Tone.Info -> Triple(cs.primaryContainer, cs.onPrimaryContainer, Icons.Rounded.Info)
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Row(Modifier.padding(horizontal = Space.s2, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
            Icon(ic, null, Modifier.size(20.dp)); Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Onboarding progress: segmented bar + "Step n of m". */
@Composable
fun StepIndicator(step: Int, total: Int, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { i ->
                val c by animateColorAsState(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "seg")
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The helper's state, drawn calmly: soft breathing halo while listening, a live level-style bar group while speaking,
 * a progress ring while connecting, and neutral when off. Never emoji.
 */
@Composable
fun StatusHero(phase: Phase, label: String, size: Dp = 168.dp) {
    val cs = MaterialTheme.colorScheme; val st = LocalStatus.current
    val active = phase == Phase.LISTENING || phase == Phase.SPEAKING
    val core by animateColorAsState(when (phase) {
        Phase.LISTENING, Phase.SPEAKING -> cs.primary
        Phase.OFFLINE -> st.warningContainer
        Phase.STOPPED -> cs.surfaceContainerHigh
        else -> cs.primaryContainer
    }, tween(Motion.medium), label = "core")
    val iconTint = when (phase) {
        Phase.LISTENING, Phase.SPEAKING -> cs.onPrimary
        Phase.OFFLINE -> st.onWarningContainer
        Phase.STOPPED -> cs.onSurfaceVariant
        else -> cs.onPrimaryContainer
    }
    val inf = rememberInfiniteTransition(label = "hero")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(Motion.breathe, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "b")
    Box(Modifier.size(size).clearAndSetSemantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (active) {
            val halo = cs.primary
            Canvas(Modifier.fillMaxSize()) {
                val r0 = this.size.minDimension * 0.34f
                for (k in 0..1) {
                    val p = ((breathe + k * 0.5f) % 1f)
                    drawCircle(halo.copy(alpha = 0.22f * (1f - p)), radius = r0 + (this.size.minDimension * 0.16f) * p)
                }
            }
        }
        if (phase == Phase.CONNECTING || phase == Phase.RECONNECTING)
            CircularProgressIndicator(Modifier.size(size * 0.78f), strokeWidth = 3.dp, color = cs.primary, trackColor = cs.primaryContainer)
        Box(Modifier.size(size * 0.64f).background(core, CircleShape), contentAlignment = Alignment.Center) {
            when (phase) {
                Phase.SPEAKING -> VoiceBars(iconTint, breathe)
                Phase.OFFLINE -> Icon(Icons.Rounded.CloudOff, null, Modifier.size(size * 0.28f), tint = iconTint)
                Phase.STOPPED -> Icon(Icons.Rounded.MicNone, null, Modifier.size(size * 0.28f), tint = iconTint)
                Phase.CONNECTING, Phase.RECONNECTING -> Icon(Icons.Rounded.Sync, null, Modifier.size(size * 0.26f), tint = iconTint)
                Phase.LISTENING -> Icon(Icons.Rounded.Mic, null, Modifier.size(size * 0.30f), tint = iconTint)
            }
        }
    }
}

@Composable
private fun VoiceBars(color: Color, t: Float) {
    Canvas(Modifier.size(56.dp, 40.dp)) {
        val n = 5; val w = size.width / (n * 2 - 1)
        for (i in 0 until n) {
            val phase = (t * 2f * Math.PI + i * 0.9).toFloat()
            val h = size.height * (0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(phase)))
            drawRoundRect(color, Offset(i * 2 * w, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

/** Centered loading state with a short message. */
@Composable
fun LoadingState(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The "next step" caption shown on Home while the helper is pointing at something. */
@Composable
fun GuidanceCard(label: String, caption: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(Space.s2 + Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null, Modifier.size(28.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(caption, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
