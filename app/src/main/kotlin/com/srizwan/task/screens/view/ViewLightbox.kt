package com.srizwan.task.screens.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.ui.ViewIcon
import com.srizwan.task.ui.appIconVector

/**
 * React `View.jsx` lightbox state `lb = { open, src, title }`: [bitmap] replaces `src` (the already-downloaded image), [title] = the
 * image file name. `openLightbox` only opens when an image is really shown; `closeLightbox` closes only if open.
 * The page keeps one of these and passes it to [ViewLightbox]; Escape (React) = the system Back button (Dialog `onDismissRequest`).
 */
class ViewLightboxState {
    var open by mutableStateOf(false); private set
    var bitmap: ImageBitmap? by mutableStateOf(null); private set
    var title by mutableStateOf(""); private set

    /** React `openLightbox`: nothing happens when no image is open or it has not loaded yet. */
    fun openLightbox(image: ImageBitmap?, name: String?) {
        if (image == null || name.isNullOrEmpty()) return
        bitmap = image; title = name; open = true
    }

    /** React `closeLightbox` (`if (lbRef.current.open) setLb({ open: false, src: '', title: '' })`). */
    fun closeLightbox() {
        if (!open) return
        open = false; bitmap = null; title = ""
    }
}

/**
 * React `<div id="lb">` — full-screen dark (`rgba(0,0,0,.88)`) popup. Tap anywhere = close; pinch to zoom and drag
 * (CSS `touch-action: pinch-zoom pan-x pan-y`); top-left a rounded title chip (file name); top-right a round white download
 * button (`#lbDl`) and, left of it, the round white close button (`#lbClose`). The download button does NOT close the popup.
 * [onDownload] = `downloadCurrent`. All icons are vectors.
 */
@Composable
fun ViewLightbox(state: ViewLightboxState, onDownload: () -> Unit) {
    if (!state.open) return
    val bmp = state.bitmap ?: return
    Dialog(
        onDismissRequest = { state.closeLightbox() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val tf = rememberTransformableState { zoom, pan, _ ->
            val s = (scale * zoom).coerceIn(1f, 6f)
            scale = s
            offset = if (s <= 1f) Offset.Zero else offset + pan
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                // tap anywhere (also on the image) = close, like `onClick={closeLightbox}`; double tap resets the zoom
                .pointerInput(Unit) { detectTapGestures(onTap = { state.closeLightbox() }, onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                .transformable(tf)
        ) {
            // #lbImg: max 96vw x 94dvh, contain, white background, shadow
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = bmp, contentDescription = state.title, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .shadow(12.dp)
                        .background(Color.White)
                )
            }

            // #lbTitle — top-left chip (pointer-events: none, so taps pass through to close)
            Text(
                state.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 10.dp, end = 112.dp)
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            // #lbClose (right 62px) and #lbDl (right 12px): 42px round white buttons
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                RoundBtn("বন্ধ করুন") { state.closeLightbox() } {
                    androidx.compose.material3.Icon(appIconVector("x"), null, Modifier.size(20.dp), tint = Color(0xFF111111))
                }
                RoundBtn("ডাউনলোড") { onDownload() } { ViewIcon("download", size = 20.dp, tint = Color(0xFF111111)) }
            }
        }
    }
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            // a tap on the button must not reach the "tap anywhere = close" handler of the background
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { icon() }
}
