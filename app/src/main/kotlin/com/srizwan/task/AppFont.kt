package com.srizwan.task

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/*
 * ---------------------------------------------------------------------------
 *  AppFont.kt  -  লেখা অনুযায়ী নিজে নিজে font বসানোর একটাই ফাইল
 * ---------------------------------------------------------------------------
 *
 *  কীভাবে কাজ করে:
 *    বাংলা লেখা   -> Anek Bangla          (res/font/anekbangla*.ttf)
 *    আরবি লেখা    -> Noto Naskh Arabic    (res/font/notonaskharabic*.ttf)
 *    ইংরেজি লেখা  -> Cormorant Garamond   (res/font/cormorant*.ttf)
 *    শুধু সংখ্যা / চিহ্ন (যেমন পিন, ফোন নাম্বার) -> বাংলা font (AppFonts.Neutral)
 *
 *  এক লাইনে মিশ্র লেখা (বাংলা + English + আরবি) থাকলে প্রতিটা অংশ আলাদা
 *  font পায়।
 *
 *  ব্যবহার (কোনো view বদলাতে হবে না):
 *    এই ফাইলে প্যাকেজ-লেভেলে একটা `Text(...)` আছে। যে ফাইলে
 *    `import androidx.compose.material3.Text` নেই, সেখানে `Text("...")` লিখলেই
 *    এই Text চলে এবং font নিজে বসে যায়। নতুন ফাইলে শুধু material3.Text
 *    import করবেন না।
 *
 *  নিয়ম:
 *    - Text-এ fontFamily (বা style-এ fontFamily) নিজে দিলে সেটাই মানা হয়।
 *    - TextField-এর ভেতরের লেখার জন্য visualTransformation = AppFontTransformation
 *    - আরবিতে Noore Huda (কুরআনের ফন্ট) চাইলে AppFonts.useQuranFont = true করুন।
 * ---------------------------------------------------------------------------
 */

object AppFonts {

    val Latin = FontFamily(
        Font(R.font.cormorantaramond, FontWeight.Normal),
        Font(R.font.cormorantgaramondbold, FontWeight.Bold)
    )

    val Bengali = FontFamily(
        Font(R.font.anekbangla, FontWeight.Normal),
        Font(R.font.anekbanglabold, FontWeight.Bold)
    )

    /** বাংলা serif চাইলে `Bengali`-এর জায়গায় এটা বসান। */
    val BengaliSerif = FontFamily(
        Font(R.font.notoserifbengali, FontWeight.Normal),
        Font(R.font.notoserifbengalibold, FontWeight.Bold)
    )

    val Arabic = FontFamily(
        Font(R.font.notonaskharabic, FontWeight.Normal),
        Font(R.font.notonaskharabicbold, FontWeight.Bold)
    )

    /** Noore Huda - কুরআনের লেখার জন্য (শুধু একটা weight আছে)। */
    val Quran = FontFamily(
        Font(R.font.noorehuda, FontWeight.Normal)
    )

    /** true হলে আরবি লেখায় Noto Naskh-এর বদলে Noore Huda চলবে। */
    val useQuranFont: Boolean = false

    /** যে লেখায় কোনো অক্ষর নেই (শুধু সংখ্যা/চিহ্ন) তার font। */
    val Neutral: FontFamily get() = Bengali
}

// ---------------------------------------------------------------------------
// Script detection
// ---------------------------------------------------------------------------
private enum class Script { LATIN, BENGALI, ARABIC, NEUTRAL }

private class ScriptRun(val start: Int, val end: Int, val script: Script)

private fun Script.family(): FontFamily = when (this) {
    Script.LATIN -> AppFonts.Latin
    Script.BENGALI -> AppFonts.Bengali
    Script.ARABIC -> if (AppFonts.useQuranFont) AppFonts.Quran else AppFonts.Arabic
    Script.NEUTRAL -> AppFonts.Neutral
}

/** null = neutral (সংখ্যা, space, চিহ্ন, emoji) - পাশের অক্ষরের font পায়। */
private fun scriptOf(ch: Char): Script? {
    val c = ch.code
    return when {
        c in 0x0980..0x09FF || c == 0x0964 || c == 0x0965 -> Script.BENGALI
        c in 0x0600..0x06FF ||
            c in 0x0750..0x077F ||
            c in 0x08A0..0x08FF ||
            c in 0xFB50..0xFDFF ||
            c in 0xFE70..0xFEFF -> Script.ARABIC
        ch.isLetter() -> Script.LATIN
        else -> null
    }
}

/** Always returns at least one run. Neutral characters join the previous run. */
private fun scriptRuns(text: CharSequence): List<ScriptRun> {
    val runs = ArrayList<ScriptRun>()
    var runStart = 0
    var current: Script? = null
    for (i in 0 until text.length) {
        val s = scriptOf(text[i]) ?: continue
        if (current == null) {
            current = s                      // leading neutrals join the first real run
        } else if (s != current) {
            runs += ScriptRun(runStart, i, current)
            runStart = i
            current = s
        }
    }
    runs += ScriptRun(runStart, text.length, current ?: Script.NEUTRAL)
    return runs
}

/** [source]-এর প্রতিটা অংশে নিজের script-এর font বসায়। আগে থেকে থাকা span-এর অগ্রাধিকার বেশি। */
private fun applyAppFonts(source: AnnotatedString, runs: List<ScriptRun>): AnnotatedString =
    buildAnnotatedString {
        append(source.text)
        runs.forEach { run ->
            if (run.end > run.start) {
                addStyle(SpanStyle(fontFamily = run.script.family()), run.start, run.end)
            }
        }
        source.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        source.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
    }

fun String.withAppFonts(): AnnotatedString =
    applyAppFonts(AnnotatedString(this), scriptRuns(this))

fun AnnotatedString.withAppFonts(): AnnotatedString =
    applyAppFonts(this, scriptRuns(this.text))

// ---------------------------------------------------------------------------
// TextField input text (user typed text also gets the right font)
// ---------------------------------------------------------------------------
object AppFontTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(text.withAppFonts(), OffsetMapping.Identity)
}

// ---------------------------------------------------------------------------
// Drop-in replacement for material3 Text
// ---------------------------------------------------------------------------
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    // caller chose a font on purpose -> respect it
    if (fontFamily != null || style.fontFamily != null) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style
        )
        return
    }

    val runs = remember(text) { scriptRuns(text) }

    if (runs.size == 1) {
        // one script only -> plain string + one font family (cheapest)
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = runs[0].script.family(),
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style
        )
    } else {
        // mixed scripts -> each run gets its own font
        val annotated = remember(text) { applyAppFonts(AnnotatedString(text), runs) }
        androidx.compose.material3.Text(
            text = annotated,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style
        )
    }
}

/** Same as above for AnnotatedString (existing spans keep priority). */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val skip = fontFamily != null || style.fontFamily != null
    val shown = if (skip) text else remember(text) { text.withAppFonts() }

    androidx.compose.material3.Text(
        text = shown,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
