package com.srizwan.task.screens.admin

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.PdfFetch
import com.srizwan.task.core.PdfFetchException
import com.srizwan.task.core.Prefs
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.HintText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Admin -> Books -> PDF viewer, step 3: full-screen viewer. The PDF is DOWNLOADED first (progress shown), then the pages are
 * shown one under another (vertical scroll).
 *
 *   AdminPdfViewer(title = book name, pdfLink = book.str("pdf_url")) { showViewer = false }
 *
 * Uses [PdfFetch] (step 1) and [PdfEngine] (step 2). The system back button / [onClose] closes it; closing while the
 * download is running cancels the download. Step 4 adds zoom, jump-to-page, night mode and the re-download button.
 *
 * Step 4-gha (big PDFs): the viewer listens to the system memory warnings and empties the page cache ([PdfEngine.trim]);
 * pages of an old width are dropped when the width changes ([PdfEngine.retainWidth]); a page is drawn only after it has stayed
 * on the screen for a moment (fast scrolling through hundreds of pages draws nothing in between).
 *
 * Step 4-ga (jump to page): the page counter in the top bar is a button; it opens a small box where a page number
 * (English or Bengali digits) is typed, with "first" / "last" shortcuts; the list then scrolls to that page.
 *
 * Step 4-kha (back button): [BackHandler] + the dialog's own back request both go to one close function; closing while
 * the download runs cancels it (the coroutine is cancelled, the network call stops, the half file is deleted); the page you
 * were on is remembered per book link ([Prefs], key "pdf_pg_...") and the viewer opens at that page next time.
 */

private val ChromeBg = Color(0xFF141517)
private val ViewerBg = Color(0xFF26282B)
private val Gold = Color(0xFFF2D27A)
private val Soft = Color(0xFFB8BDC4)
private val ErrTxt = Color(0xFFFF8A80)

private sealed interface PdfPhase {
    data class Loading(val done: Long, val total: Long) : PdfPhase
    data class Failed(val message: String) : PdfPhase
    data object Ready : PdfPhase
}

/** Prefs key of the last page seen for this link. */
private fun pageKey(link: String): String = "pdf_pg_" + PdfFetch.firstLink(link).hashCode()

private fun savedPage(link: String): Int = Prefs.getString(pageKey(link))?.toIntOrNull()?.coerceAtLeast(1) ?: 1

@Composable
fun AdminPdfViewer(title: String, pdfLink: String, onClose: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var phase by remember { mutableStateOf<PdfPhase>(PdfPhase.Loading(0L, -1L)) }
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var force by remember { mutableStateOf(false) }
    val startPage = remember(pdfLink) { savedPage(pdfLink) }      // page to open at (last page seen before)
    var current by remember { mutableIntStateOf(startPage) }
    val currentNow by rememberUpdatedState(current)
    val readyNow by rememberUpdatedState(phase is PdfPhase.Ready)

    // remember the page: a moment after scrolling stops, and again when the viewer leaves the screen
    LaunchedEffect(current, phase) {
        if (phase is PdfPhase.Ready) { delay(400); Prefs.putString(pageKey(pdfLink), current.toString()) }
    }
    val close: () -> Unit = {
        if (readyNow) Prefs.putString(pageKey(pdfLink), currentNow.toString())
        onClose()
    }
    BackHandler(onBack = close)

    // 4-ga: the list state lives here so the top bar can scroll it
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (startPage - 1).coerceAtLeast(0)
    )
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }

    // download -> open; runs again when "try again" is pressed (attempt changes). Leaving the screen cancels it.
    LaunchedEffect(attempt) {
        engine?.closeAsync(); engine = null
        phase = PdfPhase.Loading(0L, -1L)
        try {
            val file = PdfFetch.fetch(ctx, pdfLink, force) { d, t -> phase = PdfPhase.Loading(d, t) }
            force = false
            val eng = try {
                PdfEngine.open(file)
            } catch (e: PdfEngineException) {
                PdfFetch.clear(ctx, pdfLink)          // a broken saved copy must not stay: the next try downloads again
                throw e
            }
            try { ensureActive() } catch (c: CancellationException) { eng.closeAsync(); throw c }   // closed while opening: do not leak the file
            engine = eng
            phase = PdfPhase.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfFetchException) {
            phase = PdfPhase.Failed(e.message ?: "PDF নামানো যায়নি")
        } catch (e: PdfEngineException) {
            phase = PdfPhase.Failed(e.message ?: "PDF খোলা যায়নি")
        } catch (e: Exception) {
            phase = PdfPhase.Failed("PDF খোলা যায়নি: " + (e.message ?: "অজানা ত্রুটি"))
        }
    }

    DisposableEffect(Unit) { onDispose { engine?.closeAsync() } }

    // 4-gha: system memory warnings -> free the drawn pages
    val engineNow by rememberUpdatedState(engine)
    DisposableEffect(Unit) {
        val cb = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) { engineNow?.trim(level) }
            override fun onLowMemory() { engineNow?.clearCache() }
            override fun onConfigurationChanged(newConfig: Configuration) {}
        }
        ctx.registerComponentCallbacks(cb)
        onDispose { ctx.unregisterComponentCallbacks(cb) }
    }

    Dialog(
        onDismissRequest = close,                                    // system back button / gesture
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(Modifier.fillMaxSize().background(ViewerBg).systemBarsPadding()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().background(ChromeBg).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = close),
                    contentAlignment = Alignment.Center
                ) { AppIcon("left", size = 20.dp, tint = Color.White) }
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                val e = engine
                if (phase is PdfPhase.Ready && e != null) {
                    Row(
                        Modifier.padding(end = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { jumpOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon("list", size = 16.dp, tint = Gold)
                        Text(
                            Fmt.bn(current) + " / " + Fmt.bn(e.pageCount), color = Gold,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                    }
                }
            }
            // body
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val p = phase
                val e = engine
                when {
                    p is PdfPhase.Loading -> PdfLoading(p.done, p.total, onCancel = close)
                    p is PdfPhase.Failed -> PdfFailed(p.message,
                        onRetry = { force = true; attempt++ }, onClose = close)
                    e != null -> PdfPages(e, listState, onPage = { current = it })
                }
            }
        }
    }

    val eng = engine
    if (jumpOpen && eng != null) {
        PdfJumpDialog(
            current = current, pageCount = eng.pageCount,
            onGo = { page -> jumpOpen = false; scope.launch { listState.scrollToItem(page - 1) } },
            onDismiss = { jumpOpen = false }
        )
    }
}

// ---- jump to page (4-ga) -------------------------------------------------------------------------------------------

/** Bengali digits -> English digits, everything else dropped; "১২৩" -> "123". */
private fun digitsOnly(t: String): String = buildString {
    for (ch in t) {
        when {
            ch in '0'..'9' -> append(ch)
            ch in '\u09E6'..'\u09EF' -> append('0' + (ch - '\u09E6'))
        }
    }
}

@Composable
private fun PdfJumpDialog(current: Int, pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    fun go() {
        val n = digitsOnly(text).toIntOrNull()
        when {
            n == null -> err = "পেইজ নম্বর লিখুন"
            n < 1 || n > pageCount -> err = "১ থেকে " + Fmt.bn(pageCount) + " এর মধ্যে নম্বর দিন"
            else -> onGo(n)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নির্দিষ্ট পেইজে যান", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText("এখন " + Fmt.bn(current) + " নম্বর পেইজে আছেন। মোট " + Fmt.bn(pageCount) + " পেইজ।")
                AppField(
                    value = text, onValueChange = { text = it; err = "" },
                    placeholder = "পেইজ নম্বর (১ - " + Fmt.bn(pageCount) + ")",
                    keyboardType = KeyboardType.Number, maxLength = 7, onAction = { go() }
                )
                if (err.isNotEmpty()) ErrText(err)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmBtn("প্রথম পেইজ", "left") { onGo(1) }
                    SmBtn("শেষ পেইজ", "download") { onGo(pageCount) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { go() }) { Text("যান", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

// ---- download progress ------------------------------------------------------------------------------------------------

private fun mb(bytes: Long): String = Fmt.bn(Math.round(bytes / 1048576.0 * 10) / 10.0) + " MB"

@Composable
private fun PdfLoading(done: Long, total: Long, onCancel: () -> Unit) {
    val pct = PdfFetch.percent(done, total)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PDF নামানো হচ্ছে…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "নামানো শেষ হলে নিজে থেকেই খুলবে", color = Soft, fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )
        if (pct != null) {
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                color = Gold, trackColor = Color(0xFF3A3D42)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                color = Gold, trackColor = Color(0xFF3A3D42)
            )
        }
        Text(
            when {
                pct != null -> Fmt.bn(pct) + "% • " + mb(done) + " / " + mb(total)
                done > 0 -> mb(done)
                else -> "সংযোগ হচ্ছে…"
            },
            color = Soft, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
        )
        SmBtn("বাতিল", "x", tone = "bad") { onCancel() }
    }
}

// ---- error ------------------------------------------------------------------------------------------------------------

@Composable
private fun PdfFailed(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = ErrTxt, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmBtn("আবার চেষ্টা", "refresh", tone = "primary") { onRetry() }
            SmBtn("বন্ধ করুন", "x", tone = "bad") { onClose() }
        }
    }
}

// ---- vertical page list -----------------------------------------------------------------------------------------------

/** The page under the middle of the screen is "the current page". */
private fun centerIndex(s: LazyListState): Int {
    val li = s.layoutInfo
    val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2
    return li.visibleItemsInfo.firstOrNull { mid >= it.offset && mid <= it.offset + it.size }?.index
        ?: s.firstVisibleItemIndex
}

@Composable
private fun PdfPages(engine: PdfEngine, listState: LazyListState, onPage: (Int) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePad = 6.dp
        val pagePx = with(density) { (maxWidth - sidePad * 2).roundToPx() }.coerceAtLeast(PdfEngine.MIN_WIDTH)
        LaunchedEffect(pagePx) { engine.retainWidth(pagePx) }          // 4-gha: old sizes are of no use any more
        LaunchedEffect(listState) { snapshotFlow { centerIndex(listState) }.collect { onPage(it + 1) } }
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePad, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(engine.pageCount, key = { it }) { i -> PdfPageItem(engine, i, pagePx) }
        }
    }
}

/** One page: a white box of the page's exact shape right away, the picture appears when it is drawn. */
@Composable
private fun PdfPageItem(engine: PdfEngine, index: Int, widthPx: Int) {
    var bmp by remember(engine, index, widthPx) { mutableStateOf(engine.cachedPage(index, widthPx)) }
    var err by remember(engine, index, widthPx) { mutableStateOf<String?>(null) }
    var retry by remember(engine, index, widthPx) { mutableIntStateOf(0) }

    LaunchedEffect(engine, index, widthPx, retry) {
        if (bmp == null) {
            err = null
            try {
                delay(80)                                            // 4-gha: scrolled past within 80 ms -> cancelled, never drawn
                bmp = engine.render(index, widthPx)
            } catch (e: CancellationException) {
                throw e                                              // scrolled away before it was drawn
            } catch (e: PdfEngineException) {
                err = e.message ?: "পেইজ আঁকা যায়নি"
            } catch (e: Exception) {
                err = "পেইজ আঁকা যায়নি"
            }
        }
    }

    val aspect = engine.aspect(index).coerceIn(0.2f, 5f)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / aspect).clip(RoundedCornerShape(2.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(
                bitmap = remember(b) { b.asImageBitmap() }, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds
            )
            err != null -> Column(
                Modifier.clickable { retry++ }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(err ?: "", color = Color(0xFFB3261E), fontSize = 12.5.sp, textAlign = TextAlign.Center)
                Text("আবার চেষ্টা করতে চাপুন", color = Color(0xFF6B7078), fontSize = 11.5.sp)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Color(0xFF9AA0A6))
                Text(Fmt.bn(index + 1), color = Color(0xFF9AA0A6), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
