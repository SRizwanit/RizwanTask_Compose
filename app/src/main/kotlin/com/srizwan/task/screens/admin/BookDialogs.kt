package com.srizwan.task.screens.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Earnings
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.finiteNum
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.HintText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * React admin/BooksTab.jsx — part 2-জ-২: new / edit book dialog and JSON import.
 * 2-ঝ-১ added: "গুনুন" (page count) button, the automatic page count after saving / importing and the import's "count" option.
 * 2-ঝ-২-ছ added: "ছবি তৈরির workflow" + split checkboxes with the output-folder hint, and the workflow start after saving.
 * 2-ঝ-২-জ added: the import dialog's "run workflow" + split options (confirm, token check, total_pages dropped for rows with a PDF link, `vm.startRun` per imported book).
 */

// ---------------------------------------------------------------------------
// New / edit dialog
// ---------------------------------------------------------------------------
private class BookForm(
    val name: String = "", val author: String = "", val cover: String = "", val pdf: String = "",
    val rate: String = "", val tot: String = "", val gu: String = "", val repo: String = "", val br: String = "", val fold: String = ""
)

private fun blank() = BookForm()
private fun fromBook(b: JSONObject) = BookForm(
    name = b.str("name").orEmpty(), author = b.str("author").orEmpty(), cover = b.str("cover_url").orEmpty(), pdf = b.str("pdf_url").orEmpty(),
    rate = if (b.isNull("page_rate")) "" else numStr(b.opt("page_rate")),
    tot = if (b.isNull("total_pages")) "" else numStr(b.opt("total_pages")),
    gu = b.str("github_username").orEmpty(), repo = b.str("repo_name").orEmpty(), br = b.str("branch").orEmpty(), fold = b.str("folder").orEmpty()
)

private val PG = Regex("page_rate|total_pages", RegexOption.IGNORE_CASE)

@Composable
private fun Lbl(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/** `label.chk`: checkbox + text. */
@Composable
private fun ChkRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** [book] = null -> "নতুন কিতাব", else "কিতাব এডিট". */
@Composable
fun BookDialog(vm: AdminViewModel, book: JSONObject?, onClose: () -> Unit) {
    val S = vm.S
    val editId: String? = book?.idStr()
    var fm by remember { mutableStateOf(if (book != null) fromBook(book) else blank()) }
    var err by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var fCounting by remember { mutableStateOf(false) }
    // React blank(): run = true, fromBook(): run = false; split is always false
    var wantRunUi by remember { mutableStateOf(book == null) }
    var splitUi by remember { mutableStateOf(false) }
    val none = remember { JSONObject() }

    /** "গুনুন": counts the images in the GitHub folder (for the form's current name / folder / repo) into the page-count field. */
    fun formCount() {
        fCounting = true; err = ""
        val bk = jsonOf("name" to fm.name.trim(), "folder" to fm.fold.trim(), "github_username" to fm.gu.trim(), "repo_name" to fm.repo.trim(), "branch" to fm.br.trim())
        AppScope.scope.launch {
            try {
                if (bk.getString("name").isEmpty()) throw Exception("আগে কিতাবের নাম দিন")
                val c = countPages(S, bk)
                fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, c.n.toString(), fm.gu, fm.repo, fm.br, fm.fold)
                AppToast.show("${c.dir} — ${Fmt.bn(c.n)}টি ছবি" + (if (c.truncated) " (তালিকা অসম্পূর্ণ হতে পারে)" else ""))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { err = "গণনা ব্যর্থ: ${e.message}" }
            finally { fCounting = false }
        }
    }

    fun save() {
        val row = jsonOf(
            "name" to fm.name.trim(), "author" to fm.author.trim(), "cover_url" to fm.cover.trim(), "pdf_url" to fm.pdf.trim(),
            "github_username" to fm.gu.trim(), "repo_name" to fm.repo.trim(), "branch" to fm.br.trim(),
            "folder" to fm.fold.trim().trim('/')
        )
        if (row.getString("name").isEmpty()) { err = "কিতাবের নাম আবশ্যক"; return }
        val rv = fm.rate.trim(); val tv = fm.tot.trim()
        val rn = rv.toDoubleOrNull()
        if (rv.isNotEmpty() && !(rn != null && rn >= 0)) { err = "সঠিক রেট দিন"; return }
        row.put("page_rate", if (rv.isEmpty()) JSONObject.NULL else rn)
        // total_pages = Math.max(0, Math.round(Number(tv)) || 0)
        row.put("total_pages", if (tv.isEmpty()) JSONObject.NULL else (tv.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Math.floor(it + 0.5).toLong() } ?: 0L).coerceAtLeast(0L))
        val wantRun = wantRunUi; val split = splitUi
        // a new book with image creation keeps total pages blank: it shows on the Task page once the images exist and are counted
        if (wantRun && editId == null) row.put("total_pages", JSONObject.NULL)
        if (wantRun) {   // validate before saving so nothing is half done
            val pu = row.getString("pdf_url")
            if (pu.isEmpty()) { err = "ছবি তৈরির জন্য পিডিএফ লিংক দিন"; return }
            if (pdfLinks(pu).size > 1) { err = "একটি কিতাবে একটাই পিডিএফ লিংক দিন"; return }
            if (ghTok(S).isEmpty()) { err = "workflow চালাতে সেটিংস ট্যাবে GitHub Token দিন"; return }
        }
        setSplitPref(split)
        err = ""; saving = true
        val name = row.getString("name")
        AppScope.scope.launch {
            var saved: JSONObject? = null
            try {
                suspend fun persist(x: JSONObject) = if (editId != null) Sb.patch("books?id=eq.${enc(editId)}", x) else Sb.post("books", x)
                try {
                    saved = persist(row).optJSONObject(0)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (PG.containsMatchIn(e.message.orEmpty())) {
                        val rest = JSONObject(row.toString()); rest.remove("page_rate"); rest.remove("total_pages")
                        saved = persist(rest).optJSONObject(0)
                        AppToast.show("কিতাব সংরক্ষিত, কিন্তু রেট/মোট পেইজ সংরক্ষণ হয়নি — $SQLHINT", true)
                    } else throw e
                }
                if (editId != null) {
                    val old = vm.books.firstOrNull { it.idStr() == editId }
                    val oldS = if (old == null || !old.has("page_rate") || old.isNull("page_rate")) "null" else numStr(old.opt("page_rate"))
                    val newS = if (row.isNull("page_rate")) "null" else numStr(row.opt("page_rate"))
                    if (oldS != newS) ActivityLog.log("admin_rate", name + " → " + (if (row.isNull("page_rate")) "ডিফল্ট" else numStr(row.opt("page_rate")) + " ৳"))
                }
                onClose(); AppToast.show("কিতাব সংরক্ষিত হয়েছে")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                err = e.message.orEmpty(); saving = false
                return@launch
            }
            saving = false
            val list = vm.reloadBooks()
            val savedId = saved?.idStr()?.takeIf { it.isNotEmpty() } ?: editId
            val bk = list.firstOrNull { it.idStr() == savedId } ?: saved
            if (wantRun) { if (bk != null) vm.startRun(bk, split) }
            // Total pages left blank: count them from the GitHub folder by itself.
            else if (row.isNull("total_pages") && bk != null && bk.idStr().isNotEmpty()) autoCount(vm, bk, true)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (editId != null) "কিতাব এডিট" else "নতুন কিতাব", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl("কিতাবের নাম *")
                AppField(fm.name, { fm = BookForm(it, fm.author, fm.cover, fm.pdf, fm.rate, fm.tot, fm.gu, fm.repo, fm.br, fm.fold) })
                Lbl("কিতাবের লেখকের নাম")
                AppField(fm.author, { fm = BookForm(fm.name, it, fm.cover, fm.pdf, fm.rate, fm.tot, fm.gu, fm.repo, fm.br, fm.fold) })
                Lbl("কিতাবের ছবির লিংক")
                AppField(fm.cover, { fm = BookForm(fm.name, fm.author, it, fm.pdf, fm.rate, fm.tot, fm.gu, fm.repo, fm.br, fm.fold) }, placeholder = "https://...", keyboardType = KeyboardType.Uri)
                Lbl("কিতাবের পিডিএফ লিংক")
                AppField(fm.pdf, { fm = BookForm(fm.name, fm.author, fm.cover, it, fm.rate, fm.tot, fm.gu, fm.repo, fm.br, fm.fold) }, placeholder = "https://...", keyboardType = KeyboardType.Uri)
                Lbl("পেইজ প্রতি রেট (৳) — ফাঁকা = ডিফল্ট রেট")
                AppField(
                    fm.rate, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, it, fm.tot, fm.gu, fm.repo, fm.br, fm.fold) },
                    placeholder = "ডিফল্ট " + numStr(Earnings.rateOf(null, S)), keyboardType = KeyboardType.Decimal
                )
                Lbl("মোট পেইজ সংখ্যা (অগ্রগতির বারের জন্য — ফাঁকা থাকলে সংরক্ষণের পর নিজে গুনে নেবে)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(fm.tot, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, it, fm.gu, fm.repo, fm.br, fm.fold) }, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    SmBtn("গুনুন", enabled = !fCounting) { formCount() }
                }
                Lbl("GitHub Username (ফাঁকা = ডিফল্ট)")
                AppField(fm.gu, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, fm.tot, it, fm.repo, fm.br, fm.fold) }, placeholder = eff(S, none, "github_username"))
                Lbl("Repo Name (ফাঁকা = ডিফল্ট)")
                AppField(fm.repo, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, fm.tot, fm.gu, it, fm.br, fm.fold) }, placeholder = eff(S, none, "repo_name"))
                Lbl("Branch (ফাঁকা = ডিফল্ট)")
                AppField(fm.br, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, fm.tot, fm.gu, fm.repo, it, fm.fold) }, placeholder = "main")
                HintText("ডিফল্ট: ${eff(S, none, "github_username")} / ${eff(S, none, "repo_name")} (সেটিংস ট্যাব থেকে বদলানো যায়)")
                Lbl("রিপোর ফোল্ডার প্রিফিক্স")
                AppField(fm.fold, { fm = BookForm(fm.name, fm.author, fm.cover, fm.pdf, fm.rate, fm.tot, fm.gu, fm.repo, fm.br, it) }, placeholder = "যেমন: bukhari (ফাঁকা থাকলে কিতাবের নাম)")
                Text(
                    buildAnnotatedString {
                        append("ফোল্ডার হবে ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("প্রিফিক্স_pages") }
                        append(", ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("প্রিফিক্স_texts") }
                        append(", ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("প্রিফিক্স_proofed.json") }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp
                )
                ChkRow(wantRunUi, { wantRunUi = it }, "সংরক্ষণের পর পিডিএফ থেকে ছবি তৈরির workflow চালান")
                ChkRow(splitUi, { splitUi = it }, "প্রতিটি পেইজ বাম-ডান দুই ভাগ (split) করুন")
                val nm = sl(fm.fold).ifEmpty { sl(fm.name) }
                HintText(
                    if (nm.isNotEmpty()) "আউটপুট ফোল্ডার: ${nm}_${S.str("pages_dir")?.takeIf { it.isNotEmpty() } ?: DEF.getValue("pages_dir")} • workflow: ${wfFile()}"
                    else "workflow: ${wfFile()}"
                )
                HintText("নতুন কিতাব Task পেইজে তখনই দেখাবে, যখন ছবি তৈরি শেষ হয়ে মোট পেইজ গণনা সম্পন্ন হবে।")
                if (err.isNotEmpty()) ErrText(err)
            }
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = !saving) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onClose) { Text("বন্ধ") } }
    )
}

// ---------------------------------------------------------------------------
// JSON import
// ---------------------------------------------------------------------------
private const val BATCH = 50
private val URL_RE = Regex("^https?://", RegexOption.IGNORE_CASE)
private fun isUrl(u: String) = URL_RE.containsMatchIn(u)
private fun keyOf(name: String?, pdf: String?) = (name ?: "").trim().lowercase() + "|" + (pdf ?: "").trim()

class ImportInfo(val total: Int, var ok: Int = 0, var dup: Int = 0, var bad: Int = 0, var noPdf: Int = 0)

/** JS String(v) for a JSON value; null / missing -> "". */
private fun jsStr(o: JSONObject, k: String): String = if (!o.has(k) || o.isNull(k)) "" else numStr(o.opt(k))

/** JS truthiness of a JSON value (null, "", 0, false = falsy). */
private fun truthy(v: Any?): Boolean = when (v) {
    null, JSONObject.NULL -> false
    is String -> v.isNotEmpty()
    is Boolean -> v
    is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
    else -> true
}

/** `String(x.a || x.b || x.c || '')` */
private fun firstOf(o: JSONObject, vararg keys: String): String {
    for (k in keys) if (o.has(k) && truthy(o.opt(k))) return numStr(o.opt(k))
    return ""
}

/** Parses the file text into `books` rows (React parseImport); throws on invalid JSON. */
fun parseImport(text: String, existing: List<JSONObject>): Pair<List<JSONObject>, ImportInfo> {
    val tok = JSONTokener(text).nextValue()
    val data: List<Any?> = when {
        tok is JSONArray -> List(tok.length()) { tok.opt(it) }
        tok is JSONObject && tok.optJSONArray("books") != null -> tok.getJSONArray("books").let { a -> List(a.length()) { a.opt(it) } }
        else -> listOf(tok)
    }
    val seen = HashSet(existing.map { keyOf(it.str("name"), it.str("pdf_url")) })
    val rows = ArrayList<JSONObject>()
    val info = ImportInfo(data.size)
    for (x in data) {
        if (x !is JSONObject) { info.bad++; continue }
        val name = jsStr(x, "name").trim()
        if (name.isEmpty()) { info.bad++; continue }
        var pdf = firstOf(x, "pdflink", "pdf_url", "pdf").trim()
        if (!isUrl(pdf)) pdf = ""                                               // wrong values such as "link", "no"
        var cover = firstOf(x, "img", "cover_url", "cover").trim()
        if (!isUrl(cover) || Regex("no-image-found", RegexOption.IGNORE_CASE).containsMatchIn(cover)) cover = ""   // placeholder image
        val k = keyOf(name, pdf)
        if (k in seen) { info.dup++; continue }
        seen.add(k)
        val row = jsonOf("name" to name, "author" to jsStr(x, "author").trim(), "cover_url" to cover, "pdf_url" to pdf)
        for (f in listOf("github_username", "repo_name", "branch")) {
            if (x.has(f) && !x.isNull(f) && jsStr(x, f).trim().isNotEmpty()) row.put(f, jsStr(x, f).trim())
        }
        if (x.has("folder") && !x.isNull("folder") && jsStr(x, "folder").trim().isNotEmpty()) row.put("folder", jsStr(x, "folder").trim().trim('/'))
        if (x.has("page_rate") && !x.isNull("page_rate") && jsStr(x, "page_rate").trim().isNotEmpty()) {
            val n = jsStr(x, "page_rate").trim().toDoubleOrNull()
            if (n != null && n >= 0) row.put("page_rate", n)
        }
        if (x.has("total_pages") && !x.isNull("total_pages") && jsStr(x, "total_pages").trim().isNotEmpty()) {
            val n = jsStr(x, "total_pages").trim().toDoubleOrNull()
            if (n != null && n >= 0) row.put("total_pages", Math.floor(n + 0.5).toLong())
        }
        if (pdf.isEmpty()) info.noPdf++
        rows.add(row); info.ok++
    }
    return rows to info
}

private suspend fun postBatch(chunk: List<JSONObject>): JSONArray {
    try {
        return Sb.post("books", JSONArray(chunk))
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        if (PG.containsMatchIn(e.message.orEmpty())) {                          // old schema: no rate / pages columns
            return Sb.post("books", JSONArray(chunk.map { r -> JSONObject(r.toString()).also { it.remove("page_rate"); it.remove("total_pages") } }))
        }
        val out = JSONArray()                                                   // batch failed: one by one
        for (row in chunk) {
            try { Sb.post("books", row).optJSONObject(0)?.let { out.put(it) } } catch (x: CancellationException) { throw x } catch (x: Exception) { /* skip this row */ }
        }
        if (out.length() == 0) throw e
        return out
    }
}

private fun readText(ctx: Context, uri: Uri): String =
    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }

private fun fileName(ctx: Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0).orEmpty() else ""
    }.orEmpty().ifEmpty { uri.lastPathSegment.orEmpty() }
} catch (e: Exception) { uri.lastPathSegment.orEmpty() }

@Composable
fun ImportDialog(vm: AdminViewModel, ask: AskController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var info by remember { mutableStateOf<ImportInfo?>(null) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    var file by remember { mutableStateOf("") }
    var impCount by remember { mutableStateOf(true) }
    var impRun by remember { mutableStateOf(false) }      // React impRun: workflow for every imported book
    var impSplit by remember { mutableStateOf(false) }    // React impSplit: left-right split

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            err = ""; rows = emptyList(); info = null; file = fileName(ctx, uri)
            val existing = vm.books
            AppScope.scope.launch {
                val text = try { withContext(Dispatchers.IO) { readText(ctx, uri) } } catch (e: CancellationException) { throw e } catch (e: Exception) { err = "ফাইল পড়া যায়নি"; return@launch }
                try {
                    val (r, i) = parseImport(text, existing)
                    rows = r; info = i
                    if (r.isEmpty()) err = "ইমপোর্ট করার মতো নতুন কিতাব পাওয়া যায়নি"
                } catch (e: CancellationException) { throw e } catch (x: Exception) { err = "JSON পড়া যায়নি: ${x.message}" }
            }
        }
    }

    fun doImport() {
        if (rows.isEmpty()) return
        val wantRun = impRun; val split = impSplit
        val src = rows
        AppScope.scope.launch {
            if (wantRun) {
                if (ghTok(vm.S).isEmpty()) { err = "workflow চালাতে সেটিংস ট্যাবে GitHub Token দিন"; return@launch }
                val n = src.count { !it.str("pdf_url").isNullOrEmpty() }
                if (!ask.confirm("${Fmt.bn(n)}টি কিতাবের জন্য আলাদা workflow চালু হবে। এটি ভারী হতে পারে। নিশ্চিত?")) return@launch
            }
            err = ""; busy = true; setSplitPref(split)
            // with image creation the JSON total_pages is ignored for books that have a PDF link:
            // they show on the Task page once the images are made and counted
            val toSave = if (wantRun) src.map { r ->
                if (!r.str("pdf_url").isNullOrEmpty()) JSONObject(r.toString()).also { it.remove("total_pages") } else r
            } else src
            var added = 0
            try {
                var i = 0
                while (i < toSave.size) {
                    prog = "সংরক্ষণ হচ্ছে… ${Fmt.bn(minOf(i + BATCH, toSave.size))}/${Fmt.bn(toSave.size)}"
                    val chunk = toSave.subList(i, minOf(i + BATCH, toSave.size))
                    val r = postBatch(chunk)
                    added += if (r.length() > 0) r.length() else chunk.size
                    i += BATCH
                }
            } catch (e: CancellationException) { throw e } catch (x: Exception) {
                err = "ইমপোর্ট ব্যর্থ (${Fmt.bn(added)}টি সংরক্ষিত হয়েছে): ${x.message}"; busy = false; prog = ""
                vm.reloadBooks()
                return@launch
            }
            prog = "তালিকা রিফ্রেশ হচ্ছে…"
            val list = vm.reloadBooks()
            AppToast.show("${Fmt.bn(added)}টি কিতাব ইমপোর্ট হয়েছে")
            val keys = toSave.map { keyOf(it.str("name"), it.str("pdf_url")) }.toSet()
            val fresh = list.filter { keyOf(it.str("name"), it.str("pdf_url")) in keys }
            if (impCount) {
                var ok = 0
                for (i in fresh.indices) {
                    if ((fresh[i].finiteNum("total_pages") ?: 0.0) > 0.0) continue
                    prog = "পেইজ গণনা ${Fmt.bn(i + 1)}/${Fmt.bn(fresh.size)}"
                    if (autoCount(vm, fresh[i], true) != null) ok++
                }
                if (fresh.isNotEmpty()) AppToast.show("স্বয়ংক্রিয় গণনা: ${Fmt.bn(ok)}টি কিতাবের পেইজ সংখ্যা পাওয়া গেছে")
            }
            if (wantRun) {
                for (i in fresh.indices) {
                    if (fresh[i].str("pdf_url").isNullOrEmpty()) continue
                    prog = "workflow চালু ${Fmt.bn(i + 1)}/${Fmt.bn(fresh.size)}"
                    vm.startRun(fresh[i], split)
                }
            }
            busy = false; prog = ""; onClose()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("JSON ইমপোর্ট", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildAnnotatedString {
                        append("JSON ফাইলে প্রতিটি কিতাবের ")
                        fun b(s: String) = withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) }
                        b("name"); append(", "); b("author"); append(", "); b("img"); append(" (কভার), "); b("pdflink")
                        append(" (পিডিএফ) থাকলে সেগুলো নেওয়া হবে। GitHub Username, Repo, Branch ও ফোল্ডার ফাঁকা থাকবে — অর্থাৎ ডিফল্ট ব্যবহার হবে। (")
                        b("page_rate"); append(", "); b("total_pages"); append(", "); b("github_username"); append(", "); b("repo_name"); append(", ")
                        b("branch"); append(", "); b("folder"); append(" থাকলে সেগুলোও নেওয়া হয়।)")
                    },
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp
                )
                Lbl("JSON ফাইল বাছুন")
                SmBtn("ফাইল বাছুন", "plus", enabled = !busy) { picker.launch("*/*") }
                if (file.isNotEmpty()) HintText("ফাইল: $file")
                info?.let { i ->
                    Text(
                        buildAnnotatedString {
                            fun b(n: Int) = withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) { append(Fmt.bn(n)) }
                            append("ফাইলে মোট "); b(i.total); append("টি • নতুন (ইমপোর্ট হবে): "); b(i.ok)
                            append(" • ডুপ্লিকেট (আগে থেকেই আছে): "); b(i.dup); append(" • অচল/নামহীন: "); b(i.bad)
                            if (i.noPdf > 0) { append(" • পিডিএফ লিংক নেই: "); b(i.noPdf) }
                        },
                        color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
                ChkRow(impCount, { impCount = it }, "ইমপোর্টের পর স্বয়ংক্রিয়ভাবে মোট পেইজ গুনুন (GitHub ফোল্ডারে ছবি থাকলে)", enabled = !busy)
                ChkRow(impRun, { impRun = it }, "ইমপোর্টের পর প্রতিটি কিতাবের জন্য পিডিএফ থেকে ছবি তৈরির workflow চালান (ভারী — সাবধানে)", enabled = !busy)
                if (impRun) ChkRow(impSplit, { impSplit = it }, "প্রতিটি পেইজ বাম-ডান দুই ভাগ (split) করুন", enabled = !busy)
                if (prog.isNotEmpty()) HintText(prog)
                if (err.isNotEmpty()) ErrText(err)
            }
        },
        confirmButton = {
            TextButton(onClick = { doImport() }, enabled = !busy && rows.isNotEmpty()) {
                Text(if (rows.isNotEmpty()) "ইমপোর্ট (${Fmt.bn(rows.size)}টি)" else "ইমপোর্ট")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("বন্ধ") } }
    )
}
