package com.srizwan.task.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.srizwan.task.RizwanBackground
import com.srizwan.task.nav.Nav
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.HintText
import com.srizwan.task.ui.PrimaryButton

/** React Support.jsx (Firebase chat) was replaced by the hosted support site — shown in a WebView. */
private const val SUPPORT_URL = "https://RizwanTask.vercel.app/Support"
private const val SUPPORT_HOST = "rizwantask.vercel.app"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SupportScreen() {
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    // Web history first (previous page); when there is none left, go back to the Profile.
    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else Nav.go("profile")
    }

    DisposableEffect(Unit) {
        onDispose {
            web?.let { w ->
                (w.parent as? ViewGroup)?.removeView(w)
                w.stopLoading()
                w.destroy()
            }
            web = null
        }
    }

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            // thin loading bar
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (progress in 1..99 && !failed) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setSupportZoom(false)
                                builtInZoomControls = false
                                displayZoomControls = false
                                allowFileAccess = false
                                allowContentAccess = false
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    failed = false
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    canGoBack = view.canGoBack()
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) failed = true
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.host.equals(SUPPORT_HOST, ignoreCase = true)) return false
                                    // WhatsApp / Telegram / mailto / other sites → outside the app
                                    return try {
                                        view.context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl(SUPPORT_URL)
                            web = this
                        }
                    }
                )

                if (failed) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.widthIn(max = 440.dp)) {
                            AppCard {
                                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "সাপোর্ট পেইজ লোড হয়নি",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    HintText("ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।")
                                    PrimaryButton("আবার চেষ্টা করুন", onClick = {
                                        failed = false
                                        web?.reload()
                                    })
                                    PrimaryButton("ফিরে যান", onClick = { Nav.go("profile") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
