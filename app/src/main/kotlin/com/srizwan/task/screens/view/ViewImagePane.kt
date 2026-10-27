package com.srizwan.task.screens.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.ui.ViewBtn
import com.srizwan.task.ui.viewColors
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The scroll positions of the image box (React `imgBoxRef` `scrollTop` / `scrollLeft`). The page ([ViewScreen]) keeps one so that
 * [ViewPageState.viewPos] (the "where I stopped" save) can read it. Values are saved in dp, so they do not depend on the phone's density.
 */
@Stable
class ViewImageScroll(val v: ScrollState, val h: ScrollState)

@Composable
fun rememberViewImageScroll(): ViewImageScroll {
    val v = rememberScrollState(); val h = rememberScrollState()
    return remember(v, h) { ViewImageScroll(v, h) }
}

/** `viewPos` of the image half: (scrollTop, scrollLeft) in dp. */
fun ViewImageScroll.posDp(density: Float): Pair<Int, Int> = (v.value / density).roundToInt() to (h.value / density).roundToInt()

/** React `.pane` of `View.css` — white card, 1 dp line, 14 dp corners (12 on a phone), thin shadow; the image and text halves share it. */
@Composable
fun ViewPaneFrame(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = viewColors()
    val r = if (LocalConfiguration.current.screenWidthDp <= 640) 12.dp else 14.dp
    val shape = RoundedCornerShape(r)
    Column(modifier.shadow(1.dp, shape).clip(shape).background(c.bg).border(1.dp, c.line, shape)) { this.content() }
}

/**
 * React `<section className="pane pane-img">` of `View.jsx` — the page image.
 *  - Bar: "ছবি" (hidden on a phone, as React `max-width: 640px`), zoom group (− / N% / +, 50 to 400 in steps of 25) and "ফিট" (back to 100%).
 *    `zoom` is saved in the config ([ViewConfig] key `zoom`). The bar scrolls sideways when too narrow (React `overflow-x: auto`).
 *  - Body: the image is `zoom`% of the box width, height by its own ratio, scrolls both ways; a tap opens the lightbox ([ViewPageState.openLightbox]).
 *    A dotted panel colour behind it (React `repeating-conic-gradient`); a white card with a shadow around the image.
 *  - While the image loads: the ring and % ([ImageLoader], React `ldr`/`ldrKey`) or the message ([ViewPageState.imgMsg]) is shown over the box.
 *  - The last scroll of the page is restored when the image shows ([ViewPageState.imgScrollX]/[ViewPageState.imgScrollY] + `imgScrollTick`),
 *    and every scroll is saved 0.4 s later ([ViewPageState.lazyView], React `onScroll={lazyView}`).
 * `onImgLoad` / `onImgError` of React are done inside [ViewPageState.loadPage] (decoding), so there is nothing to wire here. All icons are vectors.
 */
@Composable
fun ViewImagePane(env: ViewEnv, st: ViewPageState, scroll: ViewImageScroll, modifier: Modifier = Modifier) {
    val c = viewColors()
    val density = LocalDensity.current.density
    val narrow = LocalConfiguration.current.screenWidthDp <= 640
    env.cfg.rev
    val zoomNow = env.cfg.cfg.optInt("zoom", 100)
    val hint: (String) -> Unit = { env.toaster.toast(it) }
    // React `zoom(d)`: 0 = 100, else the old value + d kept between 50 and 400
    val setZoom: (Int) -> Unit = { d ->
        val z = if (d == 0) 100 else (env.cfg.cfg.optInt("zoom", 100) + d).coerceIn(50, 400)
        env.cfg.setCfg(JSONObject().put("zoom", z))
    }
    val btn = if (narrow) 26.dp else 32.dp
    val fs = if (narrow) 11.sp else 12.sp
    val ico = if (narrow) 14.dp else 16.dp

    ViewPaneFrame(modifier) {
        // ---- the bar ----
        ViewPaneBar(narrow) {
            if (!narrow) Text("ছবি", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            // React `.seg` — one rounded box holding − / N% / +
            val seg = RoundedCornerShape(8.dp)
            Row(
                Modifier.height(IntrinsicSize.Min).clip(seg).background(c.bg).border(1.dp, c.line, seg)
                    .semantics { contentDescription = "ছবির জুম" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewBtn(
                    { setZoom(-25) }, icon = "minus", flat = true, hint = "ছোট করুন", desc = "জুম আউট", onHint = hint,
                    height = btn, width = btn, iconSize = ico
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
                Box(Modifier.fillMaxHeight().defaultMinSize(minWidth = if (narrow) 40.dp else 52.dp).padding(horizontal = if (narrow) 3.dp else 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "$zoomNow%", color = c.fg, fontSize = fs, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
                ViewBtn(
                    { setZoom(25) }, icon = "plus", flat = true, hint = "বড় করুন", desc = "জুম ইন", onHint = hint,
                    height = btn, width = btn, iconSize = ico
                )
            }
            // React `#zFit` — icon + "ফিট" (icon only on a phone)
            ViewBtn(
                { setZoom(0) }, icon = "fit", label = if (narrow) null else "ফিট", hint = "ছবি প্যানেলে ফিট করুন (১০০%)", desc = "ফিট", onHint = hint,
                height = btn, width = if (narrow) btn else null, radius = 8.dp, iconSize = ico, fontSize = fs, padH = 9.dp
            )
        }

        // ---- the body ----
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).background(c.bg).drawBehind {
                // React `repeating-conic-gradient(panel 0 25%, bg 0 50%) 0 0 / 20px 20px`: a 10 px panel square in the top-right of every 20 px tile
                val t = 20.dp.toPx(); val q = t / 2
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) { drawRect(c.panel, Offset(x + q, y), Size(q, q)); x += t }
                    y += t
                }
            }
        ) {
            val boxW = maxWidth
            val bmp = st.image
            // restore the last scroll once the image is laid out (React `scrollTo(pv.il, pv.it)` in `onImgLoad`; a new page = 0, 0)
            LaunchedEffect(st.imgScrollTick) {
                if (st.image == null) return@LaunchedEffect
                withFrameNanos { }; withFrameNanos { }
                scroll.v.scrollTo((st.imgScrollY * density).roundToInt())
                scroll.h.scrollTo((st.imgScrollX * density).roundToInt())
            }
            // React `onScroll={lazyView}` — save the place 0.4 s after the last scroll
            LaunchedEffect(scroll) {
                snapshotFlow { scroll.v.value to scroll.h.value }.collect { st.lazyView() }
            }
            Box(Modifier.fillMaxSize().verticalScroll(scroll.v).horizontalScroll(scroll.h)) {
                if (bmp != null && bmp.height > 0) {
                    val src = remember { MutableInteractionSource() }
                    Image(
                        bitmap = bmp, contentDescription = null, contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.High,
                        modifier = Modifier.width(boxW * (zoomNow / 100f)).aspectRatio(bmp.width.toFloat() / bmp.height)
                            .shadow(6.dp).background(Color.White)
                            .clickable(interactionSource = src, indication = null, onClickLabel = "বড় করে দেখতে ক্লিক করুন") { st.openLightbox() }
                    )
                }
            }
            // React `.msg` — over the box: the loader ring or a message, a 70% panel behind; it does not take touches
            if (st.ldrOn || st.imgMsg.isNotEmpty()) {
                Box(
                    Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.7f)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (st.ldrOn) key(st.ldrKey) { ImageLoader(st.ldrPct) }
                    else Text(st.imgMsg, color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * React `.bar` of `View.css` — the strip on top of each half: 38 dp (34 on a phone), the panel tint, a line below, scrolls sideways when too narrow
 * (React `overflow-x: auto`). [content] gets the inner width, so a part (the text half's "N অক্ষর") can be pushed to the right edge (React `margin-left: auto`).
 */
@Composable
fun ViewPaneBar(narrow: Boolean, content: @Composable RowScope.(Dp) -> Unit) {
    val c = viewColors()
    val padH = if (narrow) 6.dp else 8.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = if (narrow) 34.dp else 38.dp)
            .background(lerp(c.bg, c.panel, 0.15f))      // color-mix(bg 85%, panel)
            .drawBehind { drawLine(c.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
    ) {
        val inner = maxWidth - padH * 2
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = padH, vertical = if (narrow) 4.dp else 5.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content(inner) }
    }
}
