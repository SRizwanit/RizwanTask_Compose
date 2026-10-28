package com.srizwan.task.screens.view

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import com.srizwan.task.R
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.ui.ViewBtn
import com.srizwan.task.ui.viewColors
import org.json.JSONObject
import kotlin.math.roundToInt

/** React `--txt` font stack: the text itself is Noto Serif Bengali; Arabic / Urdu letters use Noorehuda (React `@font-face` `unicode-range`). */
private val EditorFont = FontFamily(Font(R.font.notoserifbengali, FontWeight.Normal), Font(R.font.notoserifbengalibold, FontWeight.Bold))
private val ArabicFont = FontFamily(Font(R.font.noorehuda, FontWeight.Normal))

/** React `unicode-range` of the Noorehuda `@font-face`: U+0600-06FF, 0750-077F, 08A0-08FF, FB50-FDFF, FE70-FEFF, 200C-200F, 0640. */
private fun isArabicChar(ch: Char): Boolean {
    val c = ch.code
    return c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0x08A0..0x08FF || c in 0xFB50..0xFDFF || c in 0xFE70..0xFEFF || c in 0x200C..0x200F
}

/**
 * The editor's look: the reading highlight ([ttsHighlightTransformation]: sentence and word colours) plus the Noorehuda font on Arabic runs.
 * The text and offsets stay unchanged (identity mapping), so cursor and selection work as usual.
 */
private fun editorTransformation(h: TtsHighlight?, hl: Color, hlw: Color): VisualTransformation {
    val hlT = ttsHighlightTransformation(h, hl, hlw)
    return VisualTransformation { text ->
        val base = hlT.filter(text).text
        val out: AnnotatedString = buildAnnotatedString {
            append(base)
            var i = 0; val n = text.length
            while (i < n) {
                if (isArabicChar(text[i])) {
                    var j = i + 1
                    while (j < n && isArabicChar(text[j])) j++
                    addStyle(SpanStyle(fontFamily = ArabicFont), i, j)
                    i = j
                } else i++
            }
        }
        TransformedText(out, OffsetMapping.Identity)
    }
}

/**
 * The state of the text box (React `taRef`: value, selection, `scrollTop`). The page ([ViewScreen]) keeps one, so [ViewPageState.viewPos]
 * (the "where I stopped" save) can read the cursor and the scroll. [tfv] follows [ViewPageState.text] both ways ([ViewTextPane] syncs them).
 */
@Stable
class ViewTextEditor(val scroll: ScrollState, initial: String) {
    var tfv by mutableStateOf(TextFieldValue(initial))
    /** Visible height of the box in px (React `clientHeight`) and the text layout (for the reading scroll). */
    var viewH = 0
    var layout: TextLayoutResult? = null
    /** A scroll the reading / restore asks for (px, null = none); [ViewTextPane] runs it and clears it. */
    var scrollReq by mutableStateOf<Int?>(null)
}

@Composable
fun rememberViewTextEditor(st: ViewPageState): ViewTextEditor {
    val sc = rememberScrollState()
    return remember(sc) { ViewTextEditor(sc, st.text) }
}

/** Clipboard text, or "" when empty / unreadable. */
private fun clipText(context: Context): String = try {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
} catch (_: Exception) { "" }

/**
 * React `<section className="pane pane-txt">` of `View.jsx` — the text of the page.
 *  - Bar: "টেক্সট" (hidden on a phone), font group (ছোট / বড়: `fs` 12 to 40 in steps of 2, saved in the config), কপি (the selected part, else all),
 *    পেস্ট (the clipboard replaces everything, cursor at the start), and on the right the info "N অক্ষর • খসড়া M" (React `txtInfo`; hidden with no page).
 *  - Box: the text, LTR, Noto Serif Bengali (Arabic runs Noorehuda), line height 1.95, size = `cfg.fs`; padding 16 x 18 dp (10 x 12 on a phone);
 *    switched off while loading ([ViewPageState.taLoading]); the placeholder ([ViewPageState.placeholder]) shows when empty.
 *    Typing -> [ViewPageState.onTextChange] (draft after 0.3 s, `tts.markStale()`) + [ViewPageState.lazyView].
 *    Every paste (long-press menu or Ctrl+V) -> [ViewPageState.onEditorPaste] (replaces the selection, cursor at the start of the pasted text).
 *    The reading highlight ([TtsController.hl]) is painted in the text, and the box scrolls to keep it in view ([ttsScrollTarget]).
 *  - Hooks given to the page: `textSelection`, `onSetCaret`, `onScrollRatio` (reading aloud), `selectAllTick` (select all when the clipboard cannot be read),
 *    `textRestoreTick` (last cursor / scroll after a page load), and `api.blurEditor` (the keyboard closes after an API result).
 * All icons are vectors; no emoji.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewTextPane(env: ViewEnv, st: ViewPageState, tts: TtsController, api: ViewApi, ed: ViewTextEditor, modifier: Modifier = Modifier) {
    val c = viewColors()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val focus = LocalFocusManager.current
    val narrow = LocalConfiguration.current.screenWidthDp <= 640
    val hint: (String) -> Unit = { env.toaster.toast(it) }
    env.cfg.rev
    val fs = env.cfg.cfg.optInt("fs", 20).coerceIn(12, 40)
    // React `fontSize(d)`: 12..40, step 2
    val setFont: (Int) -> Unit = { d -> env.cfg.setCfg(JSONObject().put("fs", (env.cfg.cfg.optInt("fs", 20) + d).coerceIn(12, 40))) }
    val btn = if (narrow) 26.dp else 32.dp
    val ico = if (narrow) 14.dp else 16.dp
    val padH = if (narrow) 12.dp else 18.dp
    val padV = if (narrow) 10.dp else 16.dp
    val padTopPx = padV.value * density
    val requester = remember { FocusRequester() }
    val hasCur = st.cur >= 0 && st.cur < st.images.size

    // ---- hooks the page / reading / API use ----
    DisposableEffect(ed, st, api) {
        st.textSelection = { ed.tfv.selection.let { it.start to it.end } }
        st.onSetCaret = { p -> ed.tfv = ed.tfv.copy(selection = TextRange(p.coerceIn(0, ed.tfv.text.length))) }
        // React `ta.scrollTop = ta.scrollHeight * r - ta.clientHeight * 0.3`
        st.onScrollRatio = { r ->
            val sh = ed.scroll.maxValue + ed.viewH
            ed.scrollReq = (sh * r - ed.viewH * 0.3f).roundToInt().coerceAtLeast(0)
        }
        api.blurEditor = { focus.clearFocus() }
        onDispose { st.textSelection = { 0 to 0 }; st.onSetCaret = {}; st.onScrollRatio = {}; api.blurEditor = {} }
    }
    // the page text -> the box (a page load, a paste button, an API result, discard ...)
    LaunchedEffect(st.text) {
        if (ed.tfv.text != st.text) ed.tfv = TextFieldValue(st.text, TextRange(ed.tfv.selection.start.coerceAtMost(st.text.length)))
    }
    // last cursor / scroll after a page load, and "cursor at the start of the pasted text" (React `setSelectionRange` + `scrollTop`)
    LaunchedEffect(st.textRestoreTick) {
        if (st.textRestoreTick == 0) return@LaunchedEffect
        ed.tfv = TextFieldValue(st.text, TextRange(st.textCursor.coerceIn(0, st.text.length)))
        withFrameNanos { }; withFrameNanos { }
        ed.scroll.scrollTo((st.textScroll * density).roundToInt())
    }
    // reading aloud asked for a scroll (`onScrollRatio`)
    LaunchedEffect(ed.scrollReq) { ed.scrollReq?.let { ed.scroll.scrollTo(it); ed.scrollReq = null } }
    // the reading highlight left the visible band -> bring it back (React `Highlight` effect)
    LaunchedEffect(tts.hl) {
        val h = tts.hl ?: return@LaunchedEffect
        val lay = ed.layout ?: return@LaunchedEffect
        ttsScrollTarget(lay, h, (ed.scroll.value - padTopPx).roundToInt(), ed.viewH)?.let { ed.scroll.scrollTo((it + padTopPx).roundToInt()) }
    }
    // select all when the clipboard cannot be read (React `ta.focus(); ta.select()`)
    LaunchedEffect(st.selectAllTick) {
        if (st.selectAllTick == 0) return@LaunchedEffect
        ed.tfv = ed.tfv.copy(selection = TextRange(0, ed.tfv.text.length))
        try { requester.requestFocus() } catch (_: Exception) { }
    }
    // React `onScroll` / `onKeyUp` / `onClick` -> `lazyView`: the place is saved 0.4 s after the last move
    LaunchedEffect(ed) { snapshotFlow { ed.scroll.value to ed.tfv.selection.start }.collect { st.lazyView() } }

    // every paste of the system menu goes through the page's own paste (React `onTaPaste`)
    val origToolbar = LocalTextToolbar.current
    val doPaste: () -> Unit = { val t = clipText(context); if (t.isNotEmpty()) st.onEditorPaste(t) }
    val toolbar = remember(origToolbar) {
        object : TextToolbar {
            override val status get() = origToolbar.status
            override fun hide() = origToolbar.hide()
            override fun showMenu(
                rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?
            ) = origToolbar.showMenu(rect, onCopyRequested, if (onPasteRequested != null) ({ origToolbar.hide(); doPaste() }) else null, onCutRequested, onSelectAllRequested)
        }
    }

    ViewPaneFrame(modifier) {
        ViewPaneBar(narrow) { inner ->
            Row(Modifier.defaultMinSize(minWidth = inner), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!narrow) Text("টেক্সট", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    // React `.seg` — ফন্ট ছোট / বড়
                    val seg = RoundedCornerShape(8.dp)
                    Row(
                        Modifier.height(IntrinsicSize.Min).clip(seg).background(c.bg).border(1.dp, c.line, seg).semantics { contentDescription = "ফন্টের মাপ" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ViewBtn({ setFont(-2) }, icon = "fontDown", flat = true, hint = "ফন্ট ছোট", desc = "ফন্ট ছোট", onHint = hint, height = btn, width = btn, iconSize = ico)
                        Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
                        ViewBtn({ setFont(2) }, icon = "fontUp", flat = true, hint = "ফন্ট বড়", desc = "ফন্ট বড়", onHint = hint, height = btn, width = btn, iconSize = ico)
                    }
                    ViewBtn(
                        { st.copyClick(context) }, icon = "copy", hint = "কপি (নির্বাচিত অংশ, না থাকলে পুরো টেক্সট)", desc = "কপি", onHint = hint,
                        height = btn, width = btn, radius = 8.dp, iconSize = ico
                    )
                    ViewBtn(
                        { st.pasteClick(context) }, icon = "paste", hint = "পেস্ট (আগের সব টেক্সট মুছে নতুন টেক্সট বসবে, কার্সর শুরুতে থাকবে)", desc = "পেস্ট", onHint = hint,
                        height = btn, width = btn, radius = 8.dp, iconSize = ico
                    )
                }
                // React `#txtInfo` — "N অক্ষর • খসড়া M"
                val info = if (hasCur) st.text.length.toString() + " অক্ষর" + (st.nDrafts().let { if (it > 0) " • খসড়া $it" else "" }) else ""
                if (info.isNotEmpty()) {
                    Text(
                        info, color = c.muted, fontSize = if (narrow) 10.sp else 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.padding(start = if (narrow) 2.dp else 6.dp)
                            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
                                onLongClick = { hint("খসড়া = এই ডিভাইসে সংরক্ষিত অসংরক্ষিত খসড়ার সংখ্যা") })
                    )
                }
            }
        }

        // ---- the text box ----
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(c.bg)) {
            val boxH = maxHeight
            ed.viewH = (boxH.value * density).roundToInt()
            val fsSp = fs.sp
            val style = TextStyle(
                color = c.fg, fontSize = fsSp, lineHeight = (fs * 1.95f).sp, fontFamily = EditorFont,
                textDirection = TextDirection.Ltr, textAlign = TextAlign.Left
            )
            val transform = remember(tts.hl, c.hl, c.hlw) { editorTransformation(tts.hl, c.hl, c.hlw) }
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                Box(Modifier.fillMaxSize().verticalScroll(ed.scroll)) {
                    BasicTextField(
                        value = ed.tfv,
                        onValueChange = { nv ->
                            val changed = nv.text != ed.tfv.text
                            ed.tfv = nv
                            if (changed) st.onTextChange(nv.text)          // React `onTaInput`: draft, `markStale`
                        },
                        enabled = !st.taLoading,
                        textStyle = style,
                        cursorBrush = SolidColor(c.fg),
                        visualTransformation = transform,
                        onTextLayout = { ed.layout = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = boxH).padding(horizontal = padH, vertical = padV)
                            .focusRequester(requester)
                            .onPreviewKeyEvent { e ->
                                // Ctrl+V of a hardware keyboard = the same page paste
                                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.V) { doPaste(); true } else false
                            },
                        decorationBox = { inner ->
                            Box {
                                if (ed.tfv.text.isEmpty() && st.placeholder.isNotEmpty()) Text(st.placeholder, style = style.copy(color = c.muted.copy(alpha = 0.8f)))
                                inner()
                            }
                        }
                    )
                }
            }
        }
    }
}
