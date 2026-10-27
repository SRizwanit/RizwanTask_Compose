package com.srizwan.task.screens.admin

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.PdfFetch
import com.srizwan.task.core.PdfFetchException
import com.srizwan.task.ui.AppIcon
import kotlinx.coroutines.CancellationException

/**
 * Admin -> Books -> PDF viewer, step 3: full-screen viewer. The PDF is DOWNLOADED first (progress shown), then the pages are
 * shown one under another (vertical scroll).
 *
 *   AdminPdfViewer(title = book name, pdfLink = book.str("pdf_url")) { showViewer = false }
 *
 * Uses [PdfFetch] (step 1) and [PdfEngine] (step 2). The system back button / [onClose] closes it; closing while the
 * download is running cancels the download. Step 4 adds zoom, jump-to-page, night mode and the re-download button.
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

@Composable
fun AdminPdfViewer(title: String, pdfLink: String, onClose: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var phase by remember { mutableStateOf<PdfPhase>(PdfPhase.Loading(0L, -1L)) }
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var force by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(1) }

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

    Dialog(
        onDismissRequest = onClose,                                  // system back button
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(Modifier.fillMaxSize().background(ViewerBg).systemBarsPadding()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().background(ChromeBg).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { AppIcon("left", size = 20.dp, tint = Color.White) }
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                val e = engine
                if (phase is PdfPhase.Ready && e != null) {
                    Text(
                        Fmt.bn(current) + " / " + Fmt.bn(e.pageCount), color = Gold,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
            // body
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val p = phase
                val e = engine
                when {
                    p is PdfPhase.Loading -> PdfLoading(p.done, p.total, onCancel = onClose)
                    p is PdfPhase.Failed -> PdfFailed(p.message,
                        onRetry = { force = true; attempt++ }, onClose = onClose)
                    e != null -> PdfPages(e, onPage = { current = it })
                }
            }
        }
    }
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
private fun PdfPages(engine: PdfEngine, onPage: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidePad = 6.dp
        val pagePx = with(density) { (maxWidth - sidePad * 2).roundToPx() }.coerceAtLeast(PdfEngine.MIN_WIDTH)
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
