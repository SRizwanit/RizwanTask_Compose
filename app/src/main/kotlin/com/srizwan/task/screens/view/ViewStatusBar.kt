package com.srizwan.task.screens.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.ui.ViewBtn
import com.srizwan.task.ui.viewColors
import kotlinx.coroutines.launch

/**
 * React `stText` / `stCls` of `View.jsx` — the one-line status of the open page: "N/M • সেভড • প্রুফড …".
 * Returns the text and whether it is the "dirty" kind (unsaved or a failed text load - React `stCls = 'dirty'`).
 * A temporary text ([ViewPageState.statusOverride], e.g. "সেভ হচ্ছে…") replaces the whole line (React `override`).
 * [apiEnabled] = React `apiEnabled`; [apiRun] = React `apiRun`. Reads only observable state, so the caller recomposes when anything changes.
 */
fun viewStatusText(st: ViewPageState, uid: String, apiEnabled: Boolean, apiRun: ApiRun?): Pair<String, Boolean> {
    st.statusOverride?.let { return it to false }
    val i = st.cur
    val hasCur = i >= 0 && i < st.images.size
    if (!hasCur) return "" to false
    val stem = st.images[i].stem
    val dirty = !st.taLoading && st.text != st.orig
    val vis = st.visIdx()
    val sb = StringBuilder()
    sb.append(vis.indexOf(i) + 1).append('/').append(vis.size)
    if (st.loadErr) sb.append(" • টেক্সট লোড ব্যর্থ (রিলোড চাপুন)")
    else if (st.taLoading) sb.append(" • লোড হচ্ছে…")
    else sb.append(if (dirty) " • অসংরক্ষিত" else if (st.sha != null) " • সেভড" else " • টেক্সট নেই (পেস্ট করে সেভ করুন)")
    if (st.proofed.contains(stem)) sb.append(" • প্রুফড").append(st.ownerTag(stem))
    val rv = st.reviews[stem]
    if (rv != null && st.owners[stem] == uid) {
        sb.append(
            when (rv.s) {
                "approved" -> " • অনুমোদিত"
                "correction" -> " • সংশোধন দরকার" + (if (rv.note.isNotEmpty()) " (" + rv.note + ")" else "")
                else -> " • রিভিউ বাকি"
            }
        )
    }
    if (!apiEnabled) sb.append(" • এপি আই বন্ধ")
    if (apiRun != null) {
        sb.append(" • ").append(ViewConfig.API_NAME.getValue(apiRun.n)).append(" চলছে")
        if (apiRun.stem != stem) sb.append(" (").append(apiRun.stem).append(')')
        if (apiRun.chars != 0) sb.append(' ').append(apiRun.chars).append(" অক্ষর")
        sb.append('…')
    }
    return sb.toString() to (dirty || st.loadErr)
}

/**
 * React `<div id="status">` of `View.jsx` — the line under the header:
 * left the image tools (ডাউনলোড, Google Lens, AI প্রম্পট কপি), in the middle the count of my finished tasks ([ViewPageState.doneCnt],
 * Bengali digits, hidden until known), on the right the status chip ([viewStatusText]; hidden while empty; warm colour when unsaved).
 * Look = the final `View.css` rules: 34 dp high, 12 dp sides, round chips, small 30 x 28 icon buttons (8 dp corners, 15 dp icons).
 * A phone has no hover, so a long press on a tool shows its title as a toast. All icons are vectors; no emoji.
 */
@Composable
fun ViewStatusBar(env: ViewEnv, st: ViewPageState, api: ViewApi, lens: ViewLens) {
    val c = viewColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    env.cfg.rev                                   // the status follows the config too
    st.listRev                                    // proofed / owners / reviews change in place
    val (text, dirty) = viewStatusText(st, env.ctx.uid, env.apiFlag.enabled, api.run)
    val done = st.doneCnt
    val hint: (String) -> Unit = { env.toaster.toast(it) }

    BoxWithConstraints(
        Modifier.fillMaxWidth().background(c.bg)
            .drawBehind { drawLine(c.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
    ) {
        val maxChip = maxWidth * 0.7f             // React `#stText{max-width:70%}`
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 34.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // React `#imgTools` — download / Lens / AI prompt
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ViewBtn(
                    { scope.launch { env.downloadCurrent(context, st.currentName) } }, icon = "download",
                    hint = "এই পেইজের মূল ছবি ডাউনলোড করুন", desc = "ডাউনলোড", onHint = hint,
                    height = 28.dp, width = 30.dp, radius = 8.dp, iconSize = 15.dp
                )
                ViewBtn(
                    { lens.open(context) }, icon = "lens",
                    hint = "এই পেইজের ছবি Google Lens-এ খুলুন (টেক্সট কপি করার জন্য)", desc = "Google Lens", onHint = hint,
                    height = 28.dp, width = 30.dp, radius = 8.dp, iconSize = 15.dp
                )
                ViewBtn(
                    { st.aiCopy(context) }, icon = "sparkle",
                    hint = "AI প্রম্পট কপি — GitHub ডাউনলোড অংশ + এই পেইজের ছবির raw লিংক + OCR প্রম্পট (Supabase), একসাথে ক্লিপবোর্ডে",
                    desc = "AI প্রম্পট কপি", onHint = hint,
                    height = 28.dp, width = 30.dp, radius = 8.dp, iconSize = 15.dp
                )
            }
            // CSS auto margins: `#doneCnt{margin:0 auto}` and `#stText{margin-left:auto}` share the free space 1 : 1 : 1
            if (done != null) {
                Spacer(Modifier.weight(1f))
                val shape = RoundedCornerShape(99.dp)
                Text(
                    ViewUtils.bnNum(done), color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.clip99(c, shape).padding(horizontal = 12.dp, vertical = 2.dp)
                        .semantics { contentDescription = "আপনার সম্পন্ন টাস্ক: " + ViewUtils.bnNum(done) }
                )
                Spacer(Modifier.weight(2f))
            } else Spacer(Modifier.weight(1f))
            if (text.isNotEmpty()) {
                val shape = RoundedCornerShape(99.dp)
                val bg = if (dirty) lerp(c.bg, c.warn, 0.12f) else c.panel
                val edge = if (dirty) lerp(c.line, c.warn, 0.35f) else c.line
                Text(
                    text, color = if (dirty) c.warn else c.muted, fontSize = 11.sp,
                    fontWeight = if (dirty) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = maxChip).background(bg, shape).border(1.dp, edge, shape)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }          // React `role="status" aria-live="polite"`
                )
            }
        }
    }
}

/** The finished-task chip: React `color-mix(in srgb, var(--ok) 12%, var(--bg))` fill with a 30% border. */
private fun Modifier.clip99(c: com.srizwan.task.ui.ViewPalette, shape: RoundedCornerShape): Modifier =
    this.background(lerp(c.bg, c.ok, 0.12f), shape).border(1.dp, lerp(c.line, c.ok, 0.30f), shape)
