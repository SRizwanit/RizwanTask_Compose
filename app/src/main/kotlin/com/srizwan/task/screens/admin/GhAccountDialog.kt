package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srizwan.task.core.AppScope
import com.srizwan.task.core.AppToast
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.ErrText
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.HintText
import kotlinx.coroutines.launch

/**
 * Add / edit one GitHub account (update_admin.md, step 2-খ). [initial] = null for a new account.
 * Required: name (shown in the book's account picker, must be unique) and token (an account never falls back to the Default token).
 * Username / Repo / Workflow may stay blank — the Default values are used then. On success [onSaved] gets the full new list
 * (already saved into `app_settings.gh_accounts` by [saveGhAccounts]).
 */
@Composable
fun GhAccountDialog(
    vm: AdminViewModel,
    accounts: List<GhAccount>,
    initial: GhAccount?,
    onSaved: (List<GhAccount>) -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var token by remember { mutableStateOf(initial?.token.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var repo by remember { mutableStateOf(initial?.repo.orEmpty()) }
    var wf by remember { mutableStateOf(initial?.wf.orEmpty()) }
    var err by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        val n = name.trim()
        val t = token.trim()
        when {
            n.isEmpty() -> { err = "অ্যাকাউন্টের নাম দিন"; return }
            t.isEmpty() -> { err = "GitHub Token দিন"; return }
            accounts.any { it.id != initial?.id && it.name.equals(n, ignoreCase = true) } -> { err = "এই নামে আরেকটি অ্যাকাউন্ট আছে"; return }
            n.equals(GhAccounts.DEFAULT_LABEL, ignoreCase = true) -> { err = "\"Default\" নামটি ব্যবহার করা যাবে না"; return }
        }
        val acc = GhAccount(initial?.id ?: GhAccounts.newId(), n, t, sl(user), sl(repo), wf.trim())
        val list = if (initial == null) accounts + acc else accounts.map { if (it.id == acc.id) acc else it }
        saving = true; err = ""
        AppScope.scope.launch {
            if (saveGhAccounts(vm, list)) {
                AppToast.show(if (initial == null) "অ্যাকাউন্ট যোগ হয়েছে" else "অ্যাকাউন্ট সংরক্ষিত হয়েছে")
                onSaved(list)
            } else saving = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(if (initial == null) "নতুন GitHub অ্যাকাউন্ট" else "GitHub অ্যাকাউন্ট এডিট", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("অ্যাকাউন্টের নাম *", first = true)
                AppField(name, { name = it; err = "" }, placeholder = "যেমন: দ্বিতীয় অ্যাকাউন্ট")
                FieldLabel("GitHub Token *")
                AppField(token, { token = it; err = "" }, placeholder = "github_pat_...", password = true)
                HintText("টোকেনে \"Actions: Read and write\" (classic হলে \"workflow\") অনুমতিও লাগবে।")
                FieldLabel("GitHub Username")
                AppField(user, { user = it }, placeholder = "ফাঁকা = Default")
                FieldLabel("Repo Name")
                AppField(repo, { repo = it }, placeholder = "ফাঁকা = Default")
                FieldLabel("ছবি তৈরির Workflow ফাইল (yml নাম)")
                AppField(wf, { wf = it }, placeholder = "ফাঁকা = Default ($WF_DEF)")
                HintText("ফাঁকা রাখা Username / Repo / Workflow-এ Default-এর মান ব্যবহার হবে। কিতাবে নিজস্ব Username / Repo দেওয়া থাকলে সেটাই আগে প্রযোজ্য।")
                if (err.isNotEmpty()) ErrText(err)
            }
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = !saving) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !saving) { Text("বন্ধ") } }
    )
}
