package com.srizwan.task.screens.admin

import com.srizwan.task.core.Fmt
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.str
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Multiple GitHub accounts (update_admin.md, step 1-খ). Pure model + helpers, no UI.
 *
 * Storage: `app_settings.gh_accounts` = JSON array text `[{id,name,token,username,repo,wf}, ...]`.
 * `Default` is NOT stored here — it is the old single setting (`github_token`, `github_username`, `repo_name`
 * and the workflow file from [wfFile]). A book picks an account with `books.gh_account` (account id);
 * blank / null / an id that no longer exists = Default, so old data behaves exactly as before.
 */
class GhAccount(
    val id: String,
    val name: String,
    val token: String,
    val username: String,
    val repo: String,
    val wf: String
) {
    fun copy(
        name: String = this.name, token: String = this.token, username: String = this.username,
        repo: String = this.repo, wf: String = this.wf
    ) = GhAccount(id, name, token, username, repo, wf)

    fun toJson(): JSONObject = jsonOf(
        "id" to id, "name" to name, "token" to token, "username" to username, "repo" to repo, "wf" to wf
    )

    /** List label: the name, else user/repo, else the id. */
    fun label(): String = name.ifEmpty { if (username.isNotEmpty() || repo.isNotEmpty()) "$username/$repo" else id }
}

/** What a GitHub call for one book should really use (see [GhAccounts.resolve]). */
class GhResolved(
    val account: GhAccount?,   // null = Default
    val token: String,
    val username: String,
    val repo: String,
    val wf: String
)

private fun String?.nzs(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

object GhAccounts {
    /** Placeholder shown in pickers / hints for the old single setting. */
    const val DEFAULT_LABEL = "Default"

    /** Accounts saved in settings `S` (app_settings row). Bad / missing JSON = empty list; entries without an id are skipped. */
    fun parse(S: JSONObject): List<GhAccount> = parseText(S.str("gh_accounts"))

    fun parseText(text: String?): List<GhAccount> {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(t)
            val out = ArrayList<GhAccount>()
            val seen = HashSet<String>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.str("id").nzs() ?: continue
                if (!seen.add(id)) continue
                out.add(
                    GhAccount(
                        id = id, name = o.str("name").orEmpty().trim(), token = o.str("token").orEmpty().trim(),
                        username = sl(o.str("username")), repo = sl(o.str("repo")), wf = o.str("wf").orEmpty().trim()
                    )
                )
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    /** Text to save into `app_settings.gh_accounts`; an empty list is saved as null (= only Default). */
    fun toText(list: List<GhAccount>): String? =
        if (list.isEmpty()) null else JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()

    fun newId(): String = "gh_" + UUID.randomUUID().toString().replace("-", "").take(10)

    fun find(S: JSONObject, id: String?): GhAccount? {
        val k = id.nzs() ?: return null
        return parse(S).firstOrNull { it.id == k }
    }

    /** Account chosen by book `b` (`gh_account`), or null = Default (also when the account was deleted). */
    fun forBook(S: JSONObject, b: JSONObject?): GhAccount? = find(S, b?.str("gh_account"))

    /**
     * Effective GitHub values for book `b`:
     *  - token: the account's token; Default = settings `github_token` (an account never falls back to the Default token)
     *  - username / repo: the book's own value first, then the account's, then the settings value, then the built-in [DEF]
     *  - wf: the account's workflow file; blank / Default = [wfFile] (the old single setting)
     */
    fun resolve(S: JSONObject, b: JSONObject?): GhResolved {
        val a = forBook(S, b)
        val bk = b ?: JSONObject()
        val user = sl(bk.str("github_username").nzs() ?: a?.username.nzs() ?: S.str("github_username").nzs() ?: DEF["github_username"])
        val repo = sl(bk.str("repo_name").nzs() ?: a?.repo.nzs() ?: S.str("repo_name").nzs() ?: DEF["repo_name"])
        val token = if (a != null) a.token else (S.str("github_token") ?: "").trim()
        val wf = a?.wf.nzs() ?: wfFile()
        return GhResolved(a, token, user, repo, wf)
    }

    /** Error text when book `b` has no GitHub token: names the account when one is chosen. */
    fun noTokenMsg(S: JSONObject, b: JSONObject?): String {
        val a = forBook(S, b)
        return if (a != null) "\"${a.label()}\" অ্যাকাউন্টে GitHub Token নেই — সেটিংস ট্যাবে টোকেন দিন"
        else "workflow চালাতে সেটিংস ট্যাবে GitHub Token দিন"
    }

    /** Books (of [list]) whose effective GitHub token is empty. */
    fun withoutToken(S: JSONObject, list: List<JSONObject>): List<JSONObject> = list.filter { resolve(S, it).token.isEmpty() }

    /** Error text for several books without a token (one book: the same text as [noTokenMsg]). */
    fun noTokenMsgMany(S: JSONObject, missing: List<JSONObject>): String =
        if (missing.size == 1) noTokenMsg(S, missing[0])
        else "${Fmt.bn(missing.size)}টি কিতাবের GitHub Token নেই — সেটিংস ট্যাবে টোকেন দিন"

    /** Short hint for a book dialog: which token source / repo / workflow will be used. */
    fun describe(S: JSONObject, b: JSONObject?): String {
        val r = resolve(S, b)
        return "${r.account?.label() ?: DEFAULT_LABEL} • ${r.username}/${r.repo} • ${r.wf}"
    }
}
