package com.srizwan.task.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.srizwan.task.LocalAppDark

/** React `View.css` `:root` colour tokens (light / dark) used by the View page UI. `hl` / `hlw` = highlight / warning highlight. */
@Immutable
class ViewPalette(
    val bg: Color, val fg: Color, val muted: Color, val line: Color, val panel: Color, val panel2: Color,
    val pri: Color, val priFg: Color, val ok: Color, val warn: Color, val err: Color, val hl: Color, val hlw: Color
)

private val ViewLight = ViewPalette(
    bg = Color(0xFFFFFFFF), fg = Color(0xFF1B1F27), muted = Color(0xFF6B7280), line = Color(0xFFE3E7EE),
    panel = Color(0xFFF4F6FA), panel2 = Color(0xFFEAEEF5), pri = Color(0xFF2563EB), priFg = Color(0xFFFFFFFF),
    ok = Color(0xFF16A34A), warn = Color(0xFFC2410C), err = Color(0xFFDC2626),
    hl = Color(0x61FACC15), hlw = Color(0x80F97316)
)

private val ViewDark = ViewPalette(
    bg = Color(0xFF14161A), fg = Color(0xFFE8EAED), muted = Color(0xFF9AA3B2), line = Color(0xFF2A2E36),
    panel = Color(0xFF1D2127), panel2 = Color(0xFF262B33), pri = Color(0xFF7AA7FF), priFg = Color(0xFF0B0D10),
    ok = Color(0xFF6FD08C), warn = Color(0xFFFBBF24), err = Color(0xFFF87171),
    hl = Color(0x42FACC15), hlw = Color(0x80FB923C)
)

/** The View page palette for the current app theme. */
@Composable
fun viewColors(): ViewPalette = if (LocalAppDark.current) ViewDark else ViewLight
