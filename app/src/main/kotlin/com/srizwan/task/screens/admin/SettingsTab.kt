package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.DEF_MIN
import com.srizwan.task.core.DEF_RATE
import com.srizwan.task.core.Sb
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.PrimaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** React admin/SettingsTab.jsx — earnings & review, API on/off (saved on toggle), GitHub and Gemini settings. */

private data class SetForm(
    val rate: String = "", val min: String = "", val wpct: String = "", val wmin: String = "",
    val gu: String = "", val repo: String = "", val br: String = "", val pages: String = "", val texts: String = "",
    val proofF: String = "", val proofD: String = "", val wf: String = "", val tok: String = "",
    val key: String = "", val mod: String = "", val tmp: String = "", val p1: String = "", val p2: String = ""
)

private fun String?.orDef(d: String): String = this?.takeIf { it.isNotEmpty() } ?: d

private fun formOf(r: JSONObject) = SetForm(
    gu = r.str("github_username").orDef(DEF.getValue("github_username")),
    repo = r.str("repo_name").orDef(DEF.getValue("repo_name")),
    br = r.str("branch").orEmpty(),
    pages = r.str("pages_dir").orDef(DEF.getValue("pages_dir")),
    texts = r.str("texts_dir").orDef(DEF.getValue("texts_dir")),
    proofF = r.str("proof_file").orDef(DEF.getValue("proof_file")),
    proofD = r.str("proof_dir").orDef(DEF.getValue("proof_dir")),
    wf = wfFile(), tok = r.str("github_token").orEmpty(),
    key = r.str("gemini_api_key").orEmpty(), mod = r.str("gemini_model").orEmpty(), tmp = numStr(r.opt("gemini_temp")),
    p1 = r.str("prompt_ocr").orEmpty(), p2 = r.str("prompt_proof").orEmpty(),
    rate = numStr(r.opt("page_rate")), min = numStr(r.opt("min_payout")),
    wpct = numStr(r.opt("warn_pct")), wmin = numStr(r.opt("warn_min"))
)

/** HTML number input: digits and one dot only. */
private fun numFilter(s: String): String = s.filter { it.isDigit() || it == '.' }

/** JS Math.round (half up). */
private fun jsRound(x: Double): Double = Math.floor(x + 0.5)

/** JS `String(v).trim() === '' ? dflt : Number(s)`. */
private fun numOr(v: String, dflt: Double): Double {
    val s = v.trim()
    return if (s.isEmpty()) dflt else s.toDoubleOrNull() ?: Double.NaN
}

@Composable
fun SettingsTab(vm: AdminViewModel, openKey: Int) {
    var f by remember { mutableStateOf(SetForm()) }
    var apiOn by remember { mutableStateOf(true) }
    var apiBusy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var paySaving by remember { mutableStateOf(false) }

    LaunchedEffect(openKey) {
        if (openKey > 0) {
            val r = vm.fetchS()
            f = formOf(r)
            apiOn = r.opt("api_enabled") != false
        }
    }

    /** GitHub + Gemini (+ API state). If api_enabled does not exist yet, the other settings are still saved. */
    fun save() {
        val t = f.tmp.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        val row = jsonOf(
            "id" to 1,
            "github_username" to sl(f.gu).ifEmpty { DEF.getValue("github_username") },
            "repo_name" to sl(f.repo).ifEmpty { DEF.getValue("repo_name") },
            "branch" to f.br.trim(),
            "pages_dir" to sl(f.pages).ifEmpty { DEF.getValue("pages_dir") },
            "texts_dir" to sl(f.texts).ifEmpty { DEF.getValue("texts_dir") },
            "proof_file" to f.proofF.trim().trimStart('/').ifEmpty { DEF.getValue("proof_file") },
            "proof_dir" to sl(f.proofD).ifEmpty { DEF.getValue("proof_dir") },
            "github_token" to f.tok.trim(),
            "gemini_api_key" to f.key.trim(),
            "gemini_model" to f.mod.trim().removePrefix("models/"),
            "gemini_temp" to t?.let { minOf(2.0, maxOf(0.0, it)) },
            "prompt_ocr" to f.p1.trim(),
            "prompt_proof" to f.p2.trim(),
            "api_enabled" to apiOn
        )
        saving = true
        AppScope.scope.launch {
            try {
                try {
                    Sb.upsert("app_settings?on_conflict=id", row)
                    vm.updateS(vm.S.copyWith(row)); AppToast.show("সেটিংস সংরক্ষিত হয়েছে")
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (Regex("api_enabled", RegexOption.IGNORE_CASE).containsMatchIn(e.message.orEmpty())) {
                        val rest = JSONObject(row.toString()).apply { remove("api_enabled") }
                        try {
                            Sb.upsert("app_settings?on_conflict=id", rest)
                            vm.updateS(vm.S.copyWith(rest))
                            AppToast.show("সেটিংস সংরক্ষিত, কিন্তু এপি আই চালু/বন্ধ সংরক্ষণ হয়নি — $SQLHINT", true)
                        } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { AppToast.show(e2.message.orEmpty(), true) }
                    } else AppToast.show(e.message.orEmpty(), true)
                }
            } finally { saving = false }
        }
    }

    /** Earnings & review settings. */
    fun savePay() {
        val rate = numOr(f.rate, DEF_RATE)
        val min = numOr(f.min, DEF_MIN.toDouble())
        val wpct = jsRound(numOr(f.wpct, 20.0))
        val wmin = jsRound(numOr(f.wmin, 5.0))
        if (!(rate >= 0) || !(min >= 0) || !(wpct >= 1 && wpct <= 100) || !(wmin >= 1)) {
            AppToast.show("সংখ্যাগুলো সঠিক দিন", true); return
        }
        val row = jsonOf("page_rate" to rate, "min_payout" to min, "warn_pct" to wpct.toLong(), "warn_min" to wmin.toLong())
        paySaving = true
        AppScope.scope.launch {
            try {
                val r = Sb.patch("app_settings?id=eq.1", row)
                if (r.length() == 0) Sb.upsert("app_settings?on_conflict=id", JSONObject(row.toString()).put("id", 1))
                val old = vm.S.opt("page_rate")
                vm.updateS(vm.S.copyWith(row)); AppToast.show("পয়েন্টস সেটিংস সংরক্ষিত হয়েছে")
                val oldTxt = numStr(old)
                ActivityLog.log(
                    "admin_settings",
                    "রেট ${numStr(rate)} পয়েন্টস, ন্যূনতম ${numStr(min)} পয়েন্টস" + if (oldTxt.isNotEmpty() && oldTxt != numStr(rate)) " (আগে $oldTxt)" else ""
                )
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val m = e.message.orEmpty()
                AppToast.show(if (Regex("page_rate|min_payout|warn_", RegexOption.IGNORE_CASE).containsMatchIn(m)) "নতুন কলাম নেই — $SQLHINT" else m, true)
            } finally { paySaving = false }
        }
    }

    /** API on/off: saved as soon as the switch is toggled. */
    fun toggleApi(on: Boolean) {
        apiBusy = true; apiOn = on
        AppScope.scope.launch {
            try {
                var r = Sb.patch("app_settings?id=eq.1", jsonOf("api_enabled" to on))
                if (r.length() == 0) r = Sb.upsert("app_settings?on_conflict=id", jsonOf("id" to 1, "api_enabled" to on))
                vm.updateS(vm.S.copyWith(jsonOf("api_enabled" to on)))
                AppToast.show(if (on) "এপি আই চালু করা হয়েছে" else "এপি আই বন্ধ করা হয়েছে")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                apiOn = !on
                val m = e.message.orEmpty()
                AppToast.show(if (Regex("api_enabled", RegexOption.IGNORE_CASE).containsMatchIn(m)) "api_enabled কলাম নেই — $SQLHINT" else m, true)
            } finally { apiBusy = false }
        }
    }

    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- পয়েন্টস ও রিভিউ ----
        AppCard {
            SectionTitle("পয়েন্টস ও রিভিউ সেটিংস")
            HintText("কিতাবে আলাদা রেট না দিলে এই ডিফল্ট রেট প্রযোজ্য। পয়েন্টস গোনা হয় শুধু অনুমোদিত পেইজে; অনুমোদনের সময়ের রেট পেইজে সংরক্ষিত হয়, তাই পরে রেট বদলালে পুরনো অনুমোদিত পেইজের পয়েন্টস বদলায় না।")
            Field("ডিফল্ট রেট — প্রতি পেইজ (পয়েন্টস)", f.rate, "0.20", KeyboardType.Decimal) { f = f.copy(rate = numFilter(it)) }
            Field("ন্যূনতম পেমেন্ট রিকোয়েস্ট (পয়েন্টস)", f.min, "50", KeyboardType.Decimal) { f = f.copy(min = numFilter(it)) }
            Field("ভুলের সতর্কবার্তা: সংশোধনের হার (%) এর বেশি হলে", f.wpct, "20", KeyboardType.Number) { f = f.copy(wpct = numFilter(it)) }
            Field("…কমপক্ষে এতগুলো পেইজ রিভিউ হওয়ার পর", f.wmin, "5", KeyboardType.Number) { f = f.copy(wmin = numFilter(it)) }
            Column(Modifier.padding(top = 16.dp)) {
                PrimaryButton("পয়েন্টস সেটিংস সংরক্ষণ", onClick = { savePay() }, enabled = !paySaving)
            }
        }

        // ---- এপি আই চালু / বন্ধ ----
        AppCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("এপি আই নিয়ন্ত্রণ (চালু / বন্ধ)", Modifier.weight(1f))
                Badge(if (apiOn) "চালু" else "বন্ধ", if (apiOn) "ok" else "bad")
            }
            HintText(
                "বন্ধ করলে View পেইজে এপি আই - ১ (ছবি থেকে টেক্সট, অটো OCR সহ) ও এপি আই - ২ (প্রুফিং) কেউই চালাতে পারবে না — " +
                    "টেক্সট আর নিজে থেকে আসবে না, পেইজের স্ট্যাটাস লাইনে \"টেক্সট নেই (পেস্ট করে সেভ করুন) • এপি আই বন্ধ\" দেখাবে। " +
                    "টগল করলেই সাথে সাথে সংরক্ষিত হয় (ইউজাররা সর্বোচ্চ ~৩০ সেকেন্ডের মধ্যে বা পেইজে ফিরলেই নতুন অবস্থা পায়)।"
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = apiOn, onCheckedChange = { toggleApi(it) }, enabled = !apiBusy)
                Text(if (apiOn) "এপি আই চালু" else "এপি আই বন্ধ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // ---- GitHub + Gemini ----
        AppCard {
            SectionTitle("GitHub সেটিংস (সব কিতাবের ডিফল্ট)")
            HintText("কোনো কিতাবে নিজস্ব GitHub Username / Repo না দিলে এই ডিফল্ট ব্যবহার হবে। মানগুলো Supabase-এ থাকে এবং সব কিতাবের View পেইজে প্রযোজ্য হয়।")
            Field("GitHub Username", f.gu, "SunniPedia") { f = f.copy(gu = it) }
            Field("Repo Name", f.repo, "pdftojpg") { f = f.copy(repo = it) }
            Field("Branch (ফাঁকা = ডিফল্ট ব্রাঞ্চ)", f.br, "main") { f = f.copy(br = it) }
            Field("পেইজ ফোল্ডার (প্রিফিক্স_ এর পরে)", f.pages, "pages") { f = f.copy(pages = it) }
            Field("টেক্সট ফোল্ডার", f.texts, "texts") { f = f.copy(texts = it) }
            Field("প্রুফড ফাইলের নাম", f.proofF, "proofed.json") { f = f.copy(proofF = it) }
            Field("প্রুফড ফোল্ডার", f.proofD, "proofed") { f = f.copy(proofD = it) }
            Field("GitHub Token", f.tok, "github_pat_...", password = true) { f = f.copy(tok = it) }
            HintText("কিতাব থেকে ছবি তৈরির workflow চালাতে টোকেনে \"Actions: Read and write\" (classic হলে \"workflow\") অনুমতিও লাগবে।")
            Field("ছবি তৈরির Workflow ফাইল (.github/workflows/ এর ভেতরের নাম)", f.wf, "ultimatepdftojpg(2).yml") {
                f = f.copy(wf = it); saveWfFile(it)
            }
            HintText("এই ফোনে সংরক্ষিত হয় (বদলালেই সাথে সাথে)। ফাঁকা রাখলে ultimatepdftojpg(2).yml ব্যবহার হবে।")

            SectionTitle("Gemini সেটিংস", Modifier.padding(top = 22.dp))
            Field("Gemini API Key", f.key, "AIza...", password = true) { f = f.copy(key = it) }
            Field("Model", f.mod, "gemini-3.5-flash-lite") { f = f.copy(mod = it) }
            Field("Temperature (০ – ২)", f.tmp, "", KeyboardType.Decimal) { f = f.copy(tmp = numFilter(it)) }
            FieldLabel("Prompt - এপি আই ১ (ছবি থেকে টেক্সট / OCR)")
            AdminTextArea(f.p1, { f = f.copy(p1 = it) })
            FieldLabel("Prompt - এপি আই ২ (টেক্সট প্রুফিং)")
            AdminTextArea(f.p2, { f = f.copy(p2 = it) })
            Column(Modifier.padding(top = 18.dp)) {
                PrimaryButton("সংরক্ষণ", onClick = { save() }, enabled = !saving)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = modifier)
}

@Composable
private fun Field(
    label: String, value: String, placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false, onChange: (String) -> Unit
) {
    FieldLabel(label)
    AppField(value = value, onValueChange = onChange, placeholder = placeholder, keyboardType = keyboardType, password = password)
}
