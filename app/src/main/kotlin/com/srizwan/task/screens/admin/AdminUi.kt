package com.srizwan.task.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.srizwan.task.ui.AnekBangla
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.EmptyArt
import com.srizwan.task.ui.hapticClickable
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.goldColor
import com.srizwan.task.ui.okColor
import com.srizwan.task.ui.warnColor
import kotlinx.coroutines.CompletableDeferred

/** React admin/ui.jsx — small shared UI pieces for the Admin tabs. */

/** `.empty` — a small illustration ([art], see `EmptyArt`; null = text only) above the message. */
@Composable
fun Empty(text: String, pad: Boolean = false, art: String? = "inbox") {
    Column(
        Modifier.fillMaxWidth().padding(if (pad) 18.dp else 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (art != null) EmptyArt(art, size = 72.dp)
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** `.tile`: big number + label; [hl] = golden highlight. */
@Composable
fun Tile(n: String, l: String, modifier: Modifier = Modifier, hl: Boolean = false, small: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape)
            // the highlighted tile has the same solid background as the others (only its border is gold)
            .background(cs.background)
            .border(1.dp, if (hl) goldColor() else cs.outline.copy(alpha = 0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(n, color = cs.onSurface, fontSize = if (small) 17.sp else 24.sp, fontWeight = FontWeight.Bold)
        Text(l, color = cs.onSurfaceVariant, fontSize = 12.5.sp)
    }
}

/** `.pg.seg3`: approved / pending / correction bar. */
@Composable
fun SegBar(a: Int, p: Int, c: Int, tot: Int, modifier: Modifier = Modifier) {
    val t = maxOf(tot, a + p + c, 1).toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(track)) {
        if (a > 0) Box(Modifier.weight(a / t).height(8.dp).background(okColor()))
        if (p > 0) Box(Modifier.weight(p / t).height(8.dp).background(goldColor()))
        if (c > 0) Box(Modifier.weight(c / t).height(8.dp).background(errColor()))
        val rest = t - a - p - c
        if (rest > 0) Box(Modifier.weight(rest / t).height(8.dp))
    }
}

/** `.chips`: single-choice filter chips. items = (value, label). */
@Composable
fun Chips(items: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (v, label) ->
            val on = v == value
            val shape = RoundedCornerShape(99.dp)
            Box(
                Modifier.clip(shape)
                    .background(if (on) cs.primary else cs.surface.copy(alpha = 0.6f))
                    .background(if (on) Color.Transparent else cs.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, if (on) cs.primary else cs.outline.copy(alpha = 0.5f), shape)
                    .hapticClickable { onPick(v) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, color = if (on) cs.onPrimary else cs.onSurface, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ask(): generic "write a reason / note" dialog.  val s = ask.ask(title = "…", required = true)  → String? (cancel = null)
// ---------------------------------------------------------------------------
class AskRequest(
    val title: String, val label: String, val value: String, val ok: String,
    val required: Boolean, val rows: Int, val result: CompletableDeferred<String?>
)

class AskController {
    var req by mutableStateOf<AskRequest?>(null)
        private set

    suspend fun ask(
        title: String, label: String = "", value: String = "", ok: String = "ঠিক আছে",
        required: Boolean = false, rows: Int = 3
    ): String? {
        val d = CompletableDeferred<String?>()
        req = AskRequest(title, label, value, ok, required, rows, d)
        try {
            return d.await()
        } finally {
            req = null
        }
    }

    internal fun done(v: String?) { req?.result?.complete(v) }

    /** window.confirm replacement: true = OK. */
    var confirmReq by mutableStateOf<ConfirmRequest?>(null)
        private set

    suspend fun confirm(message: String, ok: String = "ঠিক আছে"): Boolean {
        val d = CompletableDeferred<Boolean>()
        confirmReq = ConfirmRequest(message, ok, d)
        try {
            return d.await()
        } finally {
            confirmReq = null
        }
    }

    internal fun confirmDone(v: Boolean) { confirmReq?.result?.complete(v) }
}

class ConfirmRequest(val message: String, val ok: String, val result: CompletableDeferred<Boolean>)

@Composable
fun rememberAskController(): AskController = remember { AskController() }

@Composable
fun AskDialog(c: AskController) {
    c.confirmReq?.let { cr ->
        AlertDialog(
            onDismissRequest = { c.confirmDone(false) },
            text = { Text(cr.message) },
            confirmButton = { TextButton(onClick = { c.confirmDone(true) }) { Text(cr.ok) } },
            dismissButton = { TextButton(onClick = { c.confirmDone(false) }) { Text("বাতিল") } }
        )
    }
    val r = c.req ?: return
    var v by remember(r) { mutableStateOf(r.value) }
    var err by remember(r) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { c.done(null) },
        title = { Text(r.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // `<label class="f">` above the textarea: wraps over several lines (a floating outlined label would be cut off).
                if (r.label.isNotEmpty()) Text(r.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedTextField(
                    value = v, onValueChange = { v = it; err = "" },
                    minLines = r.rows, modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = v.trim()
                if (r.required && t.isEmpty()) err = "কিছু লিখুন" else c.done(t)
            }) { Text(r.ok) }
        },
        dismissButton = { TextButton(onClick = { c.done(null) }) { Text("বন্ধ") } }
    )
}


/** `.badge` — tone: "" (neutral) / "ok" / "warn" / "bad" / "gold". */
@Composable
fun Badge(text: String, tone: String = "") {
    val cs = MaterialTheme.colorScheme
    val c: Color = when (tone) { "ok" -> okColor(); "warn" -> warnColor(); "bad" -> errColor(); "gold" -> goldColor(); else -> cs.onSurfaceVariant }
    Box(Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** `.li`: bordered list card. */
@Composable
fun LiCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape)
            .background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outline.copy(alpha = 0.5f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** `button.sm` — tone: "" / "primary" (button.primary) / "ok" (button.ok) / "warn" (button.warnb) / "bad" (button.danger). [enabled] = !disabled; [compact] = padding 2px 8px (small "কপি" button). */
@Composable
fun SmBtn(text: String, icon: String? = null, tone: String = "", enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c: Color = when (tone) { "ok" -> okColor(); "warn" -> warnColor(); "bad" -> errColor(); "primary" -> cs.onPrimary; else -> cs.onSurface }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(when (tone) { "" -> cs.surfaceVariant.copy(alpha = 0.55f); "primary" -> cs.primary; else -> Color.Transparent })
            .border(1.dp, when (tone) { "" -> cs.outline.copy(alpha = 0.6f); "primary" -> cs.primary; else -> c }, shape)
            .hapticClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 2.dp else 7.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) AppIcon(icon, size = 15.dp, tint = c)
        if (text.isNotEmpty()) Text(text, color = c, fontSize = 13.sp)
    }
}


/** `textarea` — multi-line field styled like AppField. */
@Composable
fun AdminTextArea(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, rows: Int = 6) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(fontFamily = AnekBangla, fontSize = 14.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary), minLines = rows,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.55f), shape)
                    .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) { inner() }
        }
    )
}


/** `.noticebox.bad` — red message box. */
@Composable
fun NoticeBox(text: String) {
    val c = errColor()
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.copy(alpha = 0.10f)).border(1.dp, c.copy(alpha = 0.5f), shape).padding(12.dp)) {
        Text(text, color = c, fontSize = 13.sp)
    }
}

/** `select` — options = (value, label); the first option is shown when [value] matches none. */
@Composable
fun AdminSelect(value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    var open by remember { mutableStateOf(false) }
    val label = (options.firstOrNull { it.first == value } ?: options.firstOrNull())?.second.orEmpty()
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(cs.surfaceVariant.copy(alpha = 0.55f), shape)
                .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = cs.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("▾", color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}
