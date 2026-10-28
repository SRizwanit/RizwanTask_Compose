package com.srizwan.task.screens.admin

import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Sb
import com.srizwan.task.core.enc
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * React admin/helpers.js — the GitHub part (GitHub Actions image-creation workflow + page counting).
 * 2-ঝ-১ uses [countPages] / [autoCount]; [dispatchBook], [fetchRunState] and [isActiveRun] are used by the run UI in 2-ঝ-২.
 */

fun ghTok(S: JSONObject): String = (S.str("github_token") ?: "").trim()
fun pdfLinks(u: String?): List<String> = (u ?: "").split(Regex("[,\\s]+")).filter { it.isNotEmpty() }

/** fetch() Response replacement: status code + body text. */
class GhResp(val code: Int, val text: String) {
    val ok: Boolean get() = code in 200..299
    fun json(): JSONObject = JSONObject(text)
}

private val ghClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

private suspend fun Call.awaitResp(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
}

/** `gh(S, b, path, opt)` — https://api.github.com/repos/<user>/<repo><path>. */
suspend fun gh(S: JSONObject, b: JSONObject, path: String, method: String = "GET", body: JSONObject? = null): GhResp {
    val repo = eff(S, b, "github_username") + "/" + eff(S, b, "repo_name")
    val rb = Request.Builder().url("https://api.github.com/repos/$repo$path")
        .header("Cache-Control", "no-store")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
    if (ghTok(S).isNotEmpty()) rb.header("Authorization", "Bearer " + ghTok(S))
    rb.method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
    try {
        return ghClient.newCall(rb.build()).awaitResp().use { r -> withContext(Dispatchers.IO) { GhResp(r.code, r.body?.string().orEmpty()) } }
    } catch (e: CancellationException) { throw e } catch (e: IOException) {
        throw Exception("ইন্টারনেট সংযোগ নেই বা খুব দুর্বল — আবার চেষ্টা করুন")
    }
}

fun ghErr(r: GhResp): String {
    val m = try { JSONObject(r.text).optString("message", "") } catch (e: Exception) { "" }
    val h = when (r.code) {
        401 -> " (টোকেন ভুল বা মেয়াদ শেষ)"
        403 -> " (টোকেনে Actions: Read and write / workflow অনুমতি নেই)"
        404 -> " (রিপো বা workflow ফাইলের নাম ভুল, অথবা টোকেনের ওই রিপোতে অ্যাক্সেস নেই)"
        422 -> " (workflow-এ workflow_dispatch নেই বা ব্রাঞ্চ/ইনপুট ভুল)"
        else -> ""
    }
    return "${r.code}: $m$h"
}

private suspend fun branchOf(S: JSONObject, b: JSONObject): String {
    var ref = eff(S, b, "branch")
    if (ref.isEmpty()) {
        val ri = gh(S, b, "")
        if (!ri.ok) throw Exception(ghErr(ri))
        ref = ri.json().optString("default_branch", "").ifEmpty { "main" }
    }
    return ref
}

/** A workflow run: [id] may be null (found later from the run list), [at] = dispatch time (ISO). */
class RunInfo(val id: String?, val at: String)

/** Current state of a run (React `st` object). status "error" carries [err]. */
class RunState(
    val status: String, val conclusion: String? = null, val url: String? = null,
    val total: Int = 0, val done: Int = 0, val step: String = "", val err: String = ""
)

/** Starts the image-creation workflow for a book (needs the GitHub token and exactly one PDF link). */
suspend fun dispatchBook(S: JSONObject, b: JSONObject, split: Boolean): RunInfo {
    if (ghTok(S).isEmpty()) throw Exception("GitHub Token দেওয়া নেই — সেটিংস ট্যাবে টোকেন দিন")
    val links = pdfLinks(b.str("pdf_url"))
    if (links.isEmpty()) throw Exception("পিডিএফ লিংক দিন")
    if (links.size > 1) throw Exception("একটি কিতাবে একটাই পিডিএফ লিংক দিন")
    val ref = branchOf(S, b)
    val at = Instant.now().toString()
    val r = gh(
        S, b, "/actions/workflows/" + enc(wfFile()) + "/dispatches", "POST",
        jsonOf(
            "ref" to ref, "return_run_details" to true,
            "inputs" to jsonOf(
                "split" to split.toString(), "source" to "download", "folder_path" to "pdf", "pdf_file" to "",
                "download_url" to links[0], "output_dir" to outDirOf(S, b), "clean_output" to "false", "push_batch" to "50"
            )
        )
    )
    if (!r.ok) throw Exception(ghErr(r))
    var id: String? = null
    try {
        val j = r.json()
        id = listOf("workflow_run_id", "run_id").firstNotNullOfOrNull { k -> j.opt(k)?.takeIf { it != JSONObject.NULL && it.toString().isNotEmpty() && it.toString() != "0" }?.toString() }
        if (id == null) {
            val m = Regex("runs/(\\d+)").find(j.optString("run_url", "").ifEmpty { j.optString("html_url", "") })
            if (m != null) id = m.groupValues[1]
        }
    } catch (e: Exception) { /* 204 (no body): the id is looked up later */ }
    return RunInfo(id, at)
}

/**
 * Current state of a run; without an id it looks for the oldest workflow_dispatch run created after dispatch time
 * (and calls [onId] when found).
 */
suspend fun fetchRunState(S: JSONObject, b: JSONObject, info: RunInfo, onId: (String) -> Unit): RunState {
    var id = info.id
    if (id == null) {
        val r = gh(S, b, "/actions/workflows/" + enc(wfFile()) + "/runs?event=workflow_dispatch&per_page=15")
        if (!r.ok) throw Exception(ghErr(r))
        val t = (Fmt.parseInstant(info.at)?.toEpochMilli() ?: 0L) - 60000
        val arr = r.json().optJSONArray("workflow_runs")
        val cand = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.getJSONObject(it) }
            .filter { (Fmt.parseInstant(it.optString("created_at"))?.toEpochMilli() ?: 0L) >= t }
            .minByOrNull { Fmt.parseInstant(it.optString("created_at"))?.toEpochMilli() ?: 0L }
            ?: return RunState("queued")
        id = cand.opt("id")?.toString() ?: ""; onId(id)
    }
    val r = gh(S, b, "/actions/runs/$id")
    if (!r.ok) throw Exception(ghErr(r))
    val run = r.json()
    val status = run.optString("status", "")
    val conclusion = if (run.isNull("conclusion")) null else run.optString("conclusion")
    val url = run.optString("html_url", "").ifEmpty { null }
    if (status != "completed") {
        try {
            val rj = gh(S, b, "/actions/runs/$id/jobs")
            if (rj.ok) {
                val jobs = rj.json().optJSONArray("jobs")
                val steps = ArrayList<JSONObject>()
                for (i in 0 until (jobs?.length() ?: 0)) {
                    val st = jobs?.getJSONObject(i)?.optJSONArray("steps") ?: continue
                    for (k in 0 until st.length()) steps.add(st.getJSONObject(k))
                }
                val cur = steps.firstOrNull { it.optString("status") == "in_progress" }
                return RunState(status, conclusion, url, total = steps.size, done = steps.count { it.optString("status") == "completed" }, step = cur?.optString("name").orEmpty())
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { /* ignore */ }
    }
    return RunState(status, conclusion, url)
}

class PageCount(val n: Int, val truncated: Boolean, val dir: String)

/** Number of page images in the book's GitHub folder (jpg / jpeg / png / webp). */
suspend fun countPages(S: JSONObject, b: JSONObject): PageCount {
    val ref = branchOf(S, b)
    val r = gh(S, b, "/git/trees/" + enc(ref) + "?recursive=1")
    if (!r.ok) throw Exception(ghErr(r))
    val j = r.json()
    val dir = outDirOf(S, b) + "/"
    val tree = j.optJSONArray("tree")
    val img = Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
    var n = 0
    for (i in 0 until (tree?.length() ?: 0)) {
        val e = tree?.getJSONObject(i) ?: continue
        if (e.optString("type") == "blob" && e.optString("path").startsWith(dir) && img.containsMatchIn(e.optString("path"))) n++
    }
    return PageCount(n, j.optBoolean("truncated", false), dir)
}

fun isActiveRun(s: RunState?): Boolean = s != null && s.status in listOf("queued", "in_progress", "waiting", "pending")

/**
 * Automatic page count: counts the GitHub folder and saves total_pages (0 images = nothing changes).
 * Returns the count, or null (not found / failed). [silent] = no toasts.
 */
suspend fun autoCount(vm: AdminViewModel, b: JSONObject, silent: Boolean): Int? {
    val name = b.str("name").orEmpty()
    try {
        val c = countPages(vm.S, b)
        if (c.n <= 0) {
            if (!silent) AppToast.show("\"$name\" — ফোল্ডারে কোনো ছবি পাওয়া যায়নি", true)
            return null
        }
        Sb.patch("books?id=eq.${enc(b.idStr())}", jsonOf("total_pages" to c.n))
        vm.patchBook(b.idStr(), jsonOf("total_pages" to c.n))
        if (!silent) AppToast.show("\"$name\" — মোট পেইজ: ${Fmt.bn(c.n)}" + (if (c.truncated) " (তালিকা অসম্পূর্ণ হতে পারে)" else ""))
        return c.n
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        if (!silent) AppToast.show("গণনা ব্যর্থ: ${e.message}", true)
        return null
    }
}
