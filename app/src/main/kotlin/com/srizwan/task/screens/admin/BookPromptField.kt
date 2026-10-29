package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.str
import com.srizwan.task.core.ViewConfig
import org.json.JSONObject

/**
 * Per-book Prompt field (update_admin.md, step 5-ক). The text "Default" (or a blank field) = the prompt from Settings
 * (`app_settings.prompt_ocr` / `prompt_proof`); any other text is this book's own prompt (`books.prompt_ocr` / `prompt_proof`).
 */
const val PROMPT_DEFAULT = "Default"

fun isDefaultPrompt(t: String?): Boolean = t.isNullOrBlank() || t.trim().equals(PROMPT_DEFAULT, ignoreCase = true)

/** Value to save into the book column: null = Default, else the trimmed prompt. */
fun promptToSave(t: String?): String? = if (isDefaultPrompt(t)) null else t!!.trim()

/** Text shown in the field for a saved book value (null / blank = "Default"). */
fun promptShown(saved: String?): String = if (isDefaultPrompt(saved)) PROMPT_DEFAULT else saved!!

/** The prompt that Settings currently gives (n = 1 OCR, 2 proofing); the built-in text when Settings has none. */
fun settingsPrompt(S: JSONObject, n: Int): String =
    (S.str(if (n == 1) "prompt_ocr" else "prompt_proof") ?: "").trim().ifEmpty { ViewConfig.API_DEF.getValue(n).prompt }

/**
 * The field showed "Default" and the text became [n]: keeps only what the person typed / pasted (anywhere in the word, or over
 * a selection); a pure deletion gives "". Compares [old] and [n] by their common start and end.
 */
fun typedOverDefault(old: String, n: String): String {
    val p = old.commonPrefixWith(n).length
    val s = old.commonSuffixWith(n).length.coerceAtMost(n.length - p)
    return n.substring(p, n.length - s)
}

@Composable
fun BookPromptField(label: String, value: String, copyFrom: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isDef = isDefaultPrompt(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Badge(if (isDef) "Default" else "নিজস্ব", if (isDef) "" else "gold")
        }
        AdminTextArea(
            value = value, rows = if (isDef) 1 else 5,
            onValueChange = { n ->
                // typing into the "Default" text replaces it by what was typed; deleting from it clears it
                onChange(if (value == PROMPT_DEFAULT && n != PROMPT_DEFAULT) typedOverDefault(value, n) else n)
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isDef) SmBtn("Default করুন", compact = true) { onChange(PROMPT_DEFAULT) }
            SmBtn("সেটিংসের প্রম্পট কপি করুন", compact = true, enabled = copyFrom.isNotEmpty()) { onChange(copyFrom) }
        }
    }
}
