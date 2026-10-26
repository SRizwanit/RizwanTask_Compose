package com.srizwan.task

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.srizwan.task.core.Auth
import com.srizwan.task.nav.AppNav
import com.srizwan.task.ui.BlockedScreen
import com.srizwan.task.ui.NetBar
import com.srizwan.task.ui.ToastHost

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme + colors live in Theme.kt (system / light / dark, saved by ThemeStore)
            RizwanTaskTheme {
                AppRoot()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root: navigation (core/nav) + global overlays
//   Splash -> Login / Signup -> Task ...   (routes: nav/Nav.kt)
//   overlays: internet bar, toast, blocked-account page
// ---------------------------------------------------------------------------
@Composable
fun AppRoot() {
    val dark = LocalAppDark.current
    val activity = LocalContext.current as ComponentActivity

    // Status / navigation bar icons follow the app theme (also when it differs from the phone's theme)
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    val blocked by Auth.blocked.collectAsState()

    Box(Modifier.fillMaxSize()) {
        AppNav()
        NetBar()
        ToastHost()
        blocked?.let { BlockedScreen(it) }
    }
}
