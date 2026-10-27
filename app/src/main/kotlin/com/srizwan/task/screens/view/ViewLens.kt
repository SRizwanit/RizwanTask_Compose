package com.srizwan.task.screens.view

import android.content.Context
import com.srizwan.task.core.ViewConfig
import com.srizwan.task.core.ViewEnv
import com.srizwan.task.core.ViewUtils
import com.srizwan.task.core.enc
import com.srizwan.task.core.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * React `openLens` (`View.jsx`) — the Google Lens button: opens Lens for the page image in its own screen ([LensActivity], a WebView).
 *
 * Public repo: Lens gets the raw GitHub link of the image (`lens.google.com/uploadbyurl?url=...`) and reads it itself.
 * Private repo (Lens cannot see it): the image is made a PNG in the app cache ([ViewUtils.toPngBytes]) and Lens opens; when Lens asks for a
 * file ("ফাইল আপলোড" / upload) this image is handed over at once. (React copied the image to the clipboard and asked to paste; a phone WebView
 * cannot paste images, so the upload request is answered instead.) Button UI joins in 4-ঞ-৩: `lens.open(context)`.
 */
class ViewLens(private val env: ViewEnv, private val st: ViewPageState, private val scope: CoroutineScope) {

    fun open(context: Context) {
        val app = context.applicationContext
        if (st.cur < 0) { env.toaster.toast("কোনো ছবি খোলা নেই", true); return }
        val im = st.images[st.cur]
        val c = env.cfg.cfg
        if (st.repoPrivate == false) {
            val branch = c.str("branch").orEmpty()
            val raw = "https://raw.githubusercontent.com/" + c.str("repo").orEmpty() + "/" +
                (if (branch.isNotEmpty()) enc(branch).replace("+", "%20") else "HEAD") + "/" +
                ViewUtils.epath(ViewConfig.repoPaths(c).pagesDir + "/" + im.name).replace("+", "%20")
            LensActivity.start(context, "https://lens.google.com/uploadbyurl?url=" + enc(raw).replace("+", "%20"))
            env.toaster.toast("Google Lens খোলা হয়েছে — সেখানে \"Text\" চেপে টেক্সট কপি করুন", false, 5000)
            return
        }
        scope.launch {
            try {
                env.toaster.toast("ছবি প্রস্তুত করা হচ্ছে…", false, 4000)
                val file = withContext(Dispatchers.IO) {
                    val png = ViewUtils.toPngBytes(env.imgCache.get(im.name).bytes)
                    val dir = File(app.cacheDir, "lens").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    File(dir, "lens_" + System.currentTimeMillis() + ".png").also { it.writeBytes(png) }
                }
                LensActivity.start(context, LensActivity.LENS_HOME, file.absolutePath)
                env.toaster.toast("Google Lens খোলা হয়েছে — \"ফাইল আপলোড\" চাপলে এই পেইজের ছবি নিজে থেকে বসবে, তারপর \"Text\" চেপে কপি করুন", false, 6000)
            } catch (e: Exception) {
                env.toaster.toast("ছবি স্বয়ংক্রিয়ভাবে দেওয়া যায়নি (" + (e.message ?: e.toString()) + ") — ডাউনলোড করে lens.google.com-এ আপলোড করুন", true, 7000)
            }
        }
    }
}
