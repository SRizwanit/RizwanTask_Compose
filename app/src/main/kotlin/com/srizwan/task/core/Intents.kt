package com.srizwan.task.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** window.open(url, '_blank') */
fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppToast.show("লিংক খোলা যায়নি", true)
    }
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
