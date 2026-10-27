package com.srizwan.task.screens.view

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * React `view/Highlight.jsx` for Compose (4-জ-৩). React draws a transparent mirror layer (`#hl`) behind the textarea; in Compose the text field
 * paints the colour itself: give the editor `visualTransformation = ttsHighlightTransformation(tts.hl, pal.hl, pal.hlw)`.
 * The sentence ([TtsHighlight.a]..[TtsHighlight.b]) gets [hl] (`--hl`), the current word [hlw] (`--hlw`) on top of it; the text itself is unchanged
 * (no offset shift, so cursor and selection behave as usual). Null [h] = no highlight.
 */
fun ttsHighlightTransformation(h: TtsHighlight?, hl: Color, hlw: Color): VisualTransformation =
    if (h == null) VisualTransformation.None else VisualTransformation { text ->
        val n = text.length
        val a = h.a.coerceIn(0, n); val b = h.b.coerceIn(a, n)
        if (b <= a) return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
        val word = h.wa >= h.a && h.wb > h.wa && h.wb <= h.b
        val wa = h.wa.coerceIn(a, b); val wb = h.wb.coerceIn(wa, b)
        val both = hlw.compositeOver(hl)                    // the word mark sits inside the sentence mark: both colours show
        val out: AnnotatedString = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(background = hl), a, b)
            if (word && wb > wa) addStyle(SpanStyle(background = both), wa, wb)   // later span wins where they overlap
        }
        TransformedText(out, OffsetMapping.Identity)
    }

/**
 * React `Highlight` `useLayoutEffect` scroll — when the highlight (the word, else the sentence) leaves the visible band
 * (above 8 % or below 80 % of the editor height), return the new scroll offset (px) that puts it at 25 % from the top; else null.
 * [layout] = the text layout of the editor, [scrollY] = current scroll, [viewH] = visible height of the editor.
 */
fun ttsScrollTarget(layout: TextLayoutResult, h: TtsHighlight, scrollY: Int, viewH: Int): Int? {
    val n = layout.layoutInput.text.length
    if (n == 0) return null
    val word = h.wa >= h.a && h.wb > h.wa && h.wb <= h.b
    val s = (if (word) h.wa else h.a).coerceIn(0, n - 1)
    val e = (if (word) h.wb else h.b).coerceIn(s + 1, n)
    val top = layout.getLineTop(layout.getLineForOffset(s))
    val bot = layout.getLineBottom(layout.getLineForOffset(e - 1))
    return if (top < scrollY + viewH * 0.08f || bot > scrollY + viewH * 0.8f) maxOf(0f, top - viewH * 0.25f).toInt() else null
}
