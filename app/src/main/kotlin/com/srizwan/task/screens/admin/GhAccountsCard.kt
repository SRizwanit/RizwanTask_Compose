package com.srizwan.task.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.Sb
import com.srizwan.task.core.jsonOf
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.HintText
import kotlinx.coroutines.CancellationException

/**
 * Settings → "GitHub অ্যাকাউন্টসমূহ" card (update_admin.md, step 2-ক): the Default account (the old single GitHub settings)
 * followed by the extra accounts saved in `app_settings.gh_accounts`. The add / edit / delete buttons appear only when the
 * matching callback is given — they are wired in step 2-খ.
 */

/** Token shown masked: the last 4 characters only. */
fun maskToken(t: String): String {
    val s = t.trim()
    return when {
        s.isEmpty() -> "টোকেন নেই"
        s.length <= 8 -> "••••"
        else -> "••••" + s.takeLast(4)
    }
}

/**
 * Saves the accounts list into `app_settings.gh_accounts` (an empty list is saved as null) and updates `vm.S`.
 * Returns true when saved; on failure shows a toast (and the SQL hint when the column is missing).
 */
suspend fun saveGhAccounts(vm: AdminViewModel, list: List<GhAccount>): Boolean {
    val row = jsonOf("gh_accounts" to GhAccounts.toText(list))
    return try {
        var r = Sb.patch("app_settings?id=eq.1", row)
        if (r.length() == 0) r = Sb.upsert("app_settings?on_conflict=id", jsonOf("id" to 1, "gh_accounts" to GhAccounts.toText(list)))
        vm.updateS(vm.S.copyWith(row))
        true
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        val m = e.message.orEmpty()
        AppToast.show(if (Regex("gh_accounts", RegexOption.IGNORE_CASE).containsMatchIn(m)) "gh_accounts কলাম নেই — $SQLHINT" else m, true)
        false
    }
}

@Composable
fun GhAccountsCard(
    accounts: List<GhAccount>,
    defUser: String, defRepo: String, defWf: String, defTokenSet: Boolean,
    onAdd: (() -> Unit)? = null,
    onEdit: ((GhAccount) -> Unit)? = null,
    onDelete: ((GhAccount) -> Unit)? = null
) {
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "GitHub অ্যাকাউন্টসমূহ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (onAdd != null) SmBtn("যোগ করুন", "plus", tone = "primary") { onAdd() }
        }
        HintText(
            "Default হলো নিচের \"GitHub সেটিংস\"-এর টোকেন, Username, Repo ও workflow। কিতাবের এডিটে অন্য অ্যাকাউন্ট বেছে নিলে " +
                "ছবি তৈরি, পেইজ গণনা ও View পেইজে সেই অ্যাকাউন্টের টোকেন/রিপো/workflow ব্যবহার হবে।"
        )
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            LiCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(GhAccounts.DEFAULT_LABEL, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Badge("ডিফল্ট", "gold")
                }
                AccountLines(defUser, defRepo, defWf, if (defTokenSet) "••••" else "টোকেন নেই")
            }
            if (accounts.isEmpty()) {
                Text("এখনো কোনো অতিরিক্ত অ্যাকাউন্ট নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            accounts.forEach { a ->
                LiCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(a.label(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (a.token.isEmpty()) Badge("টোকেন নেই", "bad")
                        if (onEdit != null) SmBtn("", "edit", compact = true) { onEdit(a) }
                        if (onDelete != null) SmBtn("", "trash", tone = "bad", compact = true) { onDelete(a) }
                    }
                    AccountLines(a.username, a.repo, a.wf, maskToken(a.token), blankHint = "Default")
                }
            }
        }
    }
}

/** Username / Repo / workflow / token lines of one account card; a blank value shows [blankHint]. */
@Composable
private fun AccountLines(user: String, repo: String, wf: String, token: String, blankHint: String = "—") {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun v(s: String) = s.ifEmpty { blankHint }
    Text("Username: ${v(user)}  •  Repo: ${v(repo)}", color = muted, fontSize = 12.sp)
    Text("Workflow: ${v(wf)}", color = muted, fontSize = 12.sp)
    Text("Token: $token", color = muted, fontSize = 12.sp)
}
