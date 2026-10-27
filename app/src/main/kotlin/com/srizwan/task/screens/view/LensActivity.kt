package com.srizwan.task.screens.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.srizwan.task.RizwanTaskTheme
import com.srizwan.task.ui.ViewIcon
import java.io.File

/**
 * Google Lens in its own screen (a separate Activity with a WebView) — the page image goes in, the text is copied from Lens ("Text" button).
 *
 * Extras: [EXTRA_URL] (page to open; default `https://lens.google.com/`), [EXTRA_IMAGE] (optional: a PNG in the app cache — when Lens asks for a
 * file to upload, the first request gets this image at once, later requests open the phone's normal file chooser).
 * Back = web history first, then closes the screen. The image file is deleted when the screen closes. All icons are vectors.
 */
class LensActivity : ComponentActivity() {
    private var imageFile: File? = null
    private var autoUsed = false
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCb = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty().ifEmpty { LENS_HOME }
        imageFile = intent.getStringExtra(EXTRA_IMAGE)?.let { File(it) }?.takeIf { it.isFile }
        setContent { RizwanTaskTheme { LensScreen(url) } }
    }

    override fun onDestroy() {
        fileCb?.onReceiveValue(null); fileCb = null
        imageFile?.delete()
        super.onDestroy()
    }

    /** Lens asked for a file: the prepared page image first (once), otherwise the phone's file chooser. */
    private fun onFileRequest(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCb?.onReceiveValue(null); fileCb = null
        val f = imageFile
        if (f != null && f.isFile && !autoUsed) {
            autoUsed = true
            cb.onReceiveValue(arrayOf(Uri.fromFile(f)))
            return true
        }
        fileCb = cb
        return try { chooser.launch(params.createIntent()); true } catch (_: Exception) { fileCb = null; cb.onReceiveValue(null); false }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LensScreen(url: String) {
        var progress by remember { mutableIntStateOf(0) }
        var failed by remember { mutableStateOf(false) }
        var web by remember { mutableStateOf<WebView?>(null) }

        BackHandler {
            val w = web
            if (w != null && w.canGoBack()) w.goBack() else finish()
        }
        DisposableEffect(Unit) {
            onDispose {
                web?.let { w -> (w.parent as? ViewGroup)?.removeView(w); w.stopLoading(); w.destroy() }
                web = null
            }
        }

        val bg = MaterialTheme.colorScheme.background
        val fg = MaterialTheme.colorScheme.onBackground
        Column(Modifier.fillMaxSize().background(bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { finish() },
                    contentAlignment = Alignment.Center
                ) { ViewIcon("left", size = 20.dp, tint = fg) }
                ViewIcon("lens", Modifier.padding(start = 6.dp), size = 18.dp, tint = fg)
                Text("Google Lens", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { failed = false; web?.reload() },
                    contentAlignment = Alignment.Center
                ) { ViewIcon("refresh", size = 18.dp, tint = fg) }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (progress in 1..99 && !failed)
                    Box(Modifier.fillMaxWidth(progress / 100f).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                allowFileAccess = false
                                allowContentAccess = true
                                // Google shows its sign-in / "unsupported browser" walls to embedded WebViews: use the plain mobile Chrome look
                                userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) { failed = false }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) failed = true
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val s = request.url.scheme
                                    if (s == "http" || s == "https") return false
                                    // intent: / market: / mailto: ... -> outside the app
                                    return try { startActivity(Intent(Intent.ACTION_VIEW, request.url)); true } catch (_: Exception) { true }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                                override fun onShowFileChooser(
                                    webView: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
                                ): Boolean = onFileRequest(cb, params)
                            }
                            loadUrl(url)
                            web = this
                        }
                    }
                )
                if (failed) {
                    Column(
                        Modifier.fillMaxSize().background(bg).padding(24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Google Lens লোড হয়নি", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("ইন্টারনেট সংযোগ দেখে উপরের রিফ্রেশ বোতাম চাপুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "lens_url"
        const val EXTRA_IMAGE = "lens_image"
        const val LENS_HOME = "https://lens.google.com/"

        /** Opens the Lens screen. [imagePath] = a PNG for the upload request (optional). */
        fun start(ctx: Context, url: String = LENS_HOME, imagePath: String? = null) {
            ctx.startActivity(
                Intent(ctx, LensActivity::class.java)
                    .putExtra(EXTRA_URL, url).apply { if (imagePath != null) putExtra(EXTRA_IMAGE, imagePath) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
