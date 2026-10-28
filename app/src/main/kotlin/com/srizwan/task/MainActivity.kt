package com.srizwan.task

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Crumb
import com.srizwan.task.core.MotionStore
import com.srizwan.task.nav.AppNav
import com.srizwan.task.push.Push
import com.srizwan.task.ui.BlockedScreen
import com.srizwan.task.ui.NetBar
import com.srizwan.task.ui.ToastHost

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Crumb.mark(this, "MainActivity.onCreate")
        enableEdgeToEdge()
        Crumb.mark(this, "MainActivity.setContent")
        setContent {
            // Theme + colors live in Theme.kt (system / light / dark, saved by ThemeStore)
            RizwanTaskTheme {
                AppRoot()
            }
        }
    }

    /** Battery saver / the phone's animation setting may have changed while the app was in the background. */
    override fun onStart() {
        super.onStart()
        Push.syncOpened()
    }

    override fun onResume() {
        super.onResume()
        MotionStore.refresh(this)
        Crumb.mark(this, "MainActivity.onResume")
    }
}

// ---------------------------------------------------------------------------
// Root: navigation (core/nav) + global overlays
//   Splash -> Login / Signup -> Task ...   (routes: nav/Nav.kt)
//   overlays: internet bar, toast, blocked-account page
// ---------------------------------------------------------------------------
/** Safe replacement for `context as ComponentActivity` — walks the wrappers, null when there is no activity. */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

@Composable
fun AppRoot() {
    val dark = LocalAppDark.current
    val activity = LocalContext.current.findComponentActivity()
    val appCtx = LocalContext.current
    LaunchedEffect(Unit) { Crumb.mark(appCtx, "AppRoot first composition") }

    // Status / navigation bar icons follow the app theme (also when it differs from the phone's theme)
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    val blocked by Auth.blocked.collectAsState()
    PushPermissionGate()

    Box(Modifier.fillMaxSize()) {
        AppNav()
        NetBar()
        ToastHost()
        blocked?.let { BlockedScreen(it) }
    }
}

/**
 * Notification permission (Android 13+ POST_NOTIFICATIONS): a short explanation first, then the system dialog.
 * Shown once after sign-in (at most twice in total); "পরে" keeps the app working, only the system notifications stay off.
 */
@Composable
private fun PushPermissionGate() {
    val ask by Push.askPermission.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not: the next check sees it */ }
    if (!ask) return
    AlertDialog(
        onDismissRequest = { Push.permissionAsked() },
        title = { Text("নোটিফিকেশন চালু করুন") },
        text = { Text("নতুন নোটিশ, পেমেন্ট অনুমোদন, রিভিউ ও রিওয়ার্ড ব্যাজের খবর ফোনে পেতে নোটিফিকেশনের অনুমতি দিন (অ্যাপ বন্ধ থাকলে প্রতি ১৫ মিনিট অন্তর চেক হয়)।") },
        confirmButton = {
            TextButton(onClick = {
                Push.permissionAsked()
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("চালু করুন") }
        },
        dismissButton = { TextButton(onClick = { Push.permissionAsked() }) { Text("পরে") } }
    )
}
