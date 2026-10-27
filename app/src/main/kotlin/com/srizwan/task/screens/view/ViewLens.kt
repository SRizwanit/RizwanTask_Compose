package com.srizwan.task.screens.view

import android.content.Context
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.core.enc
import com.srizwan.task.core.openUrl
import com.srizwan.task.core.str
import kotlinx.coroutines.CoroutineScope

/**
 * React `openLens` (`View.jsx`) — the Google Lens button: opens Google Lens in the phone's BROWSER (no in-app WebView any more).
 *
 * Public repo: Lens gets the raw GitHub link of the image (`lens.google.com/uploadbyurl?url=...`) and reads it itself.
 * Private repo (Lens cannot see the image): the browser opens `lens.google.com`; the page image has to be downloaded (ডাউনলোড button)
 * and uploaded there by hand — a browser cannot be given a file from here.
 * Button UI: `lens.open(context)`.
 */
class ViewLens(private val env: ViewEnv, private val st: ViewPageState, private val scope: CoroutineScope) {

    fun open(context: Context) {
        if (st.cur < 0) { env.toaster.toast("কোনো ছবি খোলা নেই", true); return }
        val im = st.images[st.cur]
        val c = env.cfg.cfg
        if (st.repoPrivate == false) {
            val branch = c.str("branch").orEmpty()
            val raw = "https://raw.githubusercontent.com/" + c.str("repo").orEmpty() + "/" +
                (if (branch.isNotEmpty()) enc(branch).replace("+", "%20") else "HEAD") + "/" +
                ViewUtils.epath(ViewConfig.repoPaths(c).pagesDir + "/" + im.name).replace("+", "%20")
            openUrl(context, "https://lens.google.com/uploadbyurl?url=" + enc(raw).replace("+", "%20"))
            env.toaster.toast("Google Lens ব্রাউজারে খোলা হয়েছে — সেখানে \"Text\" চেপে টেক্সট কপি করুন", false, 5000)
            return
        }
        openUrl(context, LENS_HOME)
        env.toaster.toast("রিপো প্রাইভেট, তাই ছবি নিজে থেকে যাবে না — আগে ডাউনলোড বোতামে ছবি নামিয়ে Lens-এ আপলোড করুন, তারপর \"Text\" চেপে কপি করুন", false, 7000)
    }

    private companion object { const val LENS_HOME = "https://lens.google.com/" }
}
