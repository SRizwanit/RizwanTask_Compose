package com.srizwan.task.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** window.open(url, '_blank') */
fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppToast.show("লিংক খোলা যায়নি", true)
    }
}

/**
 * Opens the phone's SYSTEM DEFAULT browser app on its own (no link, no text of ours).
 * The old "app browser" selector intent failed on many phones, so:
 *  1. find the default browser by asking the system who handles a plain https link ([PackageManager.resolveActivity] with
 *     MATCH_DEFAULT_ONLY gives the app the user chose as default) and start that app's launcher intent;
 *  2. if that is not possible, open a blank-ish https link pinned to that same package (so it is still the default browser);
 *  3. last fallback: the old selector intent, then a plain https link (the system picks the default).
 * `<queries>` for `VIEW https` is already in the manifest, so the package is visible on Android 11+.
 */
fun openBrowserApp(ctx: Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    val pkg: String? = try {
        val ri = ctx.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName?.takeIf { it.isNotEmpty() && it != "android" && it != ctx.packageName }
    } catch (_: Exception) { null }
    if (pkg != null) {
        try {
            val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) { ctx.startActivity(launch.addFlags(flags)); return }
        } catch (_: Exception) { /* next */ }
        try { ctx.startActivity(Intent(probe).setPackage(pkg).addFlags(flags)); return } catch (_: Exception) { /* next */ }
    }
    try {
        ctx.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).addFlags(flags)); return
    } catch (_: Exception) { /* next */ }
    try { ctx.startActivity(Intent(probe).addFlags(flags)) } catch (_: Exception) { AppToast.show("ব্রাউজার খোলা যায়নি", true) }
}

/** https://wa.me/<admin>?text=... */
fun openWhatsApp(ctx: Context, message: String) =
    openUrl(ctx, "https://wa.me/${Cfg.ADMIN_WA}?text=${enc(message)}")

/** navigator.clipboard.writeText(text) — true when the text was copied. */
fun copyText(ctx: Context, text: String): Boolean = try {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
    true
} catch (e: Exception) {
    false
}
