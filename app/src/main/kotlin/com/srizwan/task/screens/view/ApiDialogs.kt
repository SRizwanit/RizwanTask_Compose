package com.srizwan.task.screens.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.str
import com.srizwan.task.ui.viewColors
import org.json.JSONObject

/** React `keyState(cfg, n)` */
private fun keyState(cfg: JSONObject, n: Int): String {
    val own = cfg.optJSONObject("api$n")?.str("key").orEmpty().isNotEmpty()
    val first = n == 2 && cfg.optJSONObject("api1")?.str("key").orEmpty().isNotEmpty()
    return if (own || first) "Key সেট আছে" else "Key নেই"
}

/** The form values of the config dialog (React `f`). */
private class ApiForm(var key: String, var model: String, var prompt: String, var temp: String, var img: Boolean, var auto: Boolean)

/** React `String(c.temp)` — 0 shows as "0", 0.5 as "0.5". */
private fun tempStr(v: Any?): String {
    val d = v?.toString()?.toDoubleOrNull() ?: return v?.toString().orEmpty()
    return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}

/** JS `parseFloat` — the number at the start of the text, or NaN-like null. */
private fun parseFloatJs(s: String): Double? =
    Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").find(s.trim())?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** React `toApiCfg(n, f)` — form values -> savable config. */
private fun toApiCfg(n: Int, f: ApiForm): JSONObject {
    val t = parseFloatJs(f.temp)
    val c = JSONObject()
        .put("key", f.key.trim())
        .put("model", f.model.trim().replace(Regex("^models/"), "").ifEmpty { "gemini-3.5-flash-lite" })
        .put("prompt", f.prompt.trim())
        .put("temp", if (t != null) t.coerceIn(0.0, 2.0) else 0.0)
    if (n == 2) c.put("sendImg", f.img)
    if (n == 1) c.put("auto", f.auto)
    return c
}

/**
 * React `view/ApiDialogs.jsx` — (1) the API-1 / API-2 picker, (2) the config dialog (Key, Model, Prompt, Temperature).
 * Every change in the config dialog is saved at once (phone storage only — never to the repo). State: [ViewApi.pickOpen] / [ViewApi.cfgN].
 * Shown by the API button ([ViewApi.apiBtnClick], 4-nga-3); `openApiCfg` is admin only.
 */
@Composable
fun ApiDialogs(env: ViewEnv, api: ViewApi, stem: String?) {
    env.cfg.rev                                  // recompose when the config changes
    if (api.pickOpen) ApiPickDialog(env, api)
    api.cfgN?.let { n -> ApiCfgDialog(env, api, n, stem) }
}

private val DlgShape = RoundedCornerShape(16.dp)

@Composable
private fun ApiPickDialog(env: ViewEnv, api: ViewApi) {
    val c = viewColors()
    val cfg = env.cfg.cfg
    Dialog(onDismissRequest = { api.closePicker() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(DlgShape).background(c.bg)
                .padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("API", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (!env.apiFlag.enabled)
                Text("এপি আই এখন বন্ধ (অ্যাডমিন সেটিংস থেকে চালু করুন) — চালানো যাবে না, শুধু কনফিগ দেখা/বদলানো যাবে।",
                    color = c.err, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            for (k in 1..2) {
                val a = cfg.optJSONObject("api$k") ?: JSONObject()
                val sub = ViewConfig.API_DEF.getValue(k).sub + " • " + a.str("model").orEmpty().ifEmpty { "—" } + " • " + keyState(cfg, k) +
                    (if (k == 1 && a.optBoolean("auto", false) && a.str("key").orEmpty().isNotEmpty()) " • অটো চালু" else "")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.panel)
                        .clickable { api.openApiCfg(k) }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(ViewConfig.API_NAME.getValue(k), color = c.fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(sub, color = c.muted, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { api.closePicker() }, border = BorderStroke(1.dp, c.line)) { Text("বন্ধ", color = c.fg) }
            }
        }
    }
}

@Composable
private fun ApiCfgDialog(env: ViewEnv, api: ViewApi, n: Int, stem: String?) {
    val c = viewColors()
    val context = LocalContext.current
    val name = ViewConfig.API_NAME.getValue(n)
    val def = ViewConfig.API_DEF.getValue(n)

    // saved values are put into the form when the dialog opens (React `useLayoutEffect([cfgN])`)
    var f by remember(n) {
        val a = env.cfg.cfg.optJSONObject("api$n") ?: JSONObject()
        mutableStateOf(ApiForm(a.str("key").orEmpty(), a.str("model").orEmpty(), a.str("prompt").orEmpty(),
            tempStr(a.opt("temp")), a.optBoolean("sendImg", false), a.optBoolean("auto", false)))
    }
    var keyHint by remember(n) { mutableStateOf("") }
    var promptHint by remember(n) { mutableStateOf("") }
    val keyFocus = remember { FocusRequester() }
    val promptFocus = remember { FocusRequester() }

    /** React `persist(nf)` — save at once, merged into the old values of this API. */
    fun persist(nf: ApiForm): JSONObject {
        val patch = toApiCfg(n, nf)
        env.cfg.setCfg { cur ->
            val next = JSONObject(cur.toString())
            val old = next.optJSONObject("api$n") ?: JSONObject()
            for (k in patch.keys()) old.put(k, patch.opt(k))
            next.put("api$n", old); next
        }
        api.onFormSaved()                        // saved again -> auto may run again
        return patch
    }
    fun edit(nf: ApiForm) { f = nf; persist(nf) }
    fun copyForm(key: String = f.key, model: String = f.model, prompt: String = f.prompt, temp: String = f.temp, img: Boolean = f.img, auto: Boolean = f.auto) =
        ApiForm(key, model, prompt, temp, img, auto)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.fg, unfocusedTextColor = c.fg, focusedBorderColor = c.pri, unfocusedBorderColor = c.line,
        cursorColor = c.pri, focusedContainerColor = c.panel, unfocusedContainerColor = c.panel
    )
    val noCaps = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    Dialog(onDismissRequest = { api.closeCfg() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 640.dp).clip(DlgShape).background(c.bg)
                .padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(name, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(def.sub + " • বর্তমান পেইজ: " + (stem ?: "—"), color = c.muted, fontSize = 12.sp)

            Text("API Key", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = f.key, onValueChange = { edit(copyForm(key = it)) }, singleLine = true, colors = fieldColors,
                modifier = Modifier.fillMaxWidth().focusRequester(keyFocus),
                placeholder = { Text(if (n == 2) "খালি রাখলে " + ViewConfig.API_NAME.getValue(1) + "-এর Key ব্যবহার হবে" else "AIza...", color = c.muted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password)
            )
            if (keyHint.isNotEmpty()) Text(keyHint, color = c.err, fontSize = 12.sp)

            Text("Model", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = f.model, onValueChange = { edit(copyForm(model = it)) }, singleLine = true, colors = fieldColors,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("gemini-3.5-flash-lite", color = c.muted) },
                keyboardOptions = noCaps
            )

            Text("Prompt", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = f.prompt, onValueChange = { edit(copyForm(prompt = it)) }, minLines = 8, maxLines = 8, colors = fieldColors,
                modifier = Modifier.fillMaxWidth().focusRequester(promptFocus), keyboardOptions = noCaps
            )
            Text(
                promptHint.ifEmpty {
                    if (n == 1) "বর্তমান পেইজের ছবি এই প্রম্পটের সাথে পাঠানো হবে; ফল টেক্সট বক্সে বসবে।"
                    else "টেক্সট বক্সের লেখা প্রুফ হবে। প্রম্পটে {{TEXT}} লিখলে টেক্সট ঠিক ঐ জায়গায় বসবে; না লিখলে প্রম্পটের নিচে যুক্ত হবে।"
                },
                color = if (promptHint.isEmpty()) c.muted else c.err, fontSize = 12.sp
            )

            Text("Temperature (0–2; OCR/প্রুফিংয়ে 0 ভালো)", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = f.temp, onValueChange = { edit(copyForm(temp = it)) }, singleLine = true, colors = fieldColors,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = VisualTransformation.None
            )

            if (n == 2) {
                CheckRow(f.img, "পেইজের ছবিও সাথে পাঠান (ছবির সাথে মিলিয়ে প্রুফ করার জন্য)") { edit(copyForm(img = it)) }
            }
            if (n == 1) {
                CheckRow(f.auto, "টেক্সট ফাঁকা পেইজ খুললে নিজে থেকেই চালান (অটো OCR)") { edit(copyForm(auto = it)) }
                Text("শর্ত: Key দেওয়া আছে, পেইজের টেক্সট ফাঁকা (txt নেই/খালি, খসড়াও নেই), পেইজটি প্রুফড নয়। প্রতি পেইজে সেশনে একবারই অটো চলে।",
                    color = c.muted, fontSize = 12.sp)
            }
            Text("কী, মডেল, প্রম্পট ইত্যাদি শুধু এই ফোনে থাকে। কিছু বদলালেই সাথে সাথে স্বয়ংক্রিয়ভাবে সংরক্ষিত হয় — একবার দিলে পরেও একই থাকবে। রিপোতে কখনো যায় না।",
                color = c.muted, fontSize = 12.sp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = {
                    val nf = copyForm(prompt = def.prompt); edit(nf)
                    env.toaster.toast("ডিফল্ট প্রম্পট বসানো ও সংরক্ষিত হয়েছে")
                }, border = BorderStroke(1.dp, c.line)) { Text("ডিফল্ট প্রম্পট", color = c.fg, fontSize = 12.sp) }
                OutlinedButton(onClick = { api.closeCfg() }, border = BorderStroke(1.dp, c.line)) { Text("বন্ধ", color = c.fg, fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = {
                    persist(f); api.closeCfg(); env.toaster.toast("$name সংরক্ষিত")
                }, border = BorderStroke(1.dp, c.line)) { Text("সংরক্ষণ", color = c.fg, fontSize = 12.sp) }
                Button(onClick = {
                    val cc = persist(f)
                    val firstKey = env.cfg.cfg.optJSONObject("api1")?.str("key").orEmpty()
                    if (cc.str("key").orEmpty().isEmpty() && !(n == 2 && firstKey.isNotEmpty())) {
                        keyHint = "API Key দিন"; keyFocus.requestFocus(); return@Button
                    }
                    if (cc.str("prompt").orEmpty().isEmpty()) {
                        promptHint = "Prompt খালি রাখা যাবে না"; promptFocus.requestFocus(); return@Button
                    }
                    api.closeCfg(); api.runApi(context, n)
                }, colors = ButtonDefaults.buttonColors(containerColor = c.pri, contentColor = c.priFg)) {
                    Text("সংরক্ষণ ও চালান", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = viewColors()
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = c.pri, uncheckedColor = c.muted, checkmarkColor = c.priFg))
        Text(label, color = c.fg, fontSize = 13.sp)
    }
}
