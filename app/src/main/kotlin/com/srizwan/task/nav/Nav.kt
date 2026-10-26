package com.srizwan.task.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Session
import com.srizwan.task.screens.LoginScreen
import com.srizwan.task.screens.PlaceholderScreen
import com.srizwan.task.screens.admin.AdminScreen
import com.srizwan.task.screens.SignupScreen
import com.srizwan.task.screens.SplashScreen
import com.srizwan.task.screens.SupportScreen
import com.srizwan.task.screens.task.TaskScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** React routes: /, /login, /signup, /task, /profile, /support, /admin, /view?book=… */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val TASK = "task"
    const val PROFILE = "profile"
    const val SUPPORT = "support"
    const val ADMIN = "admin"
    const val VIEW = "view"
    const val VIEW_PATTERN = "view?book={book}&page={page}"

    val NAMES = listOf(LOGIN, SIGNUP, TASK, PROFILE, SUPPORT, ADMIN, VIEW)
}

/**
 * nav.js `go()` bridge. Accepts the same strings as the React code:
 * "task", "task#guide", "view?book=1&page=2", "login.html", "/profile".
 */
object Nav {
    private var controller: NavHostController? = null

    private val _reload = MutableStateFlow(0)
    /** Increasing number: the current screen is re-created when it changes (page reload). */
    val reloadKey: StateFlow<Int> = _reload.asStateFlow()

    /** The part after '#' of the last go() call (e.g. "guide" for go("task#guide")). Read once with [consumeHash]. */
    private var pendingHash: String? = null
    fun consumeHash(): String? = pendingHash.also { pendingHash = null }

    internal fun attach(c: NavHostController) { controller = c }
    internal fun detach(c: NavHostController) { if (controller === c) controller = null }

    private val TARGET = Regex("^/?([A-Za-z0-9_-]+)(?:\\.html)?([?#].*)?$")

    fun go(to: String, replace: Boolean = false) {
        val c = controller ?: return
        val m = TARGET.matchEntire(to.trim()) ?: return
        var name = m.groupValues[1]
        if (name == "index") name = Routes.SPLASH
        var rest = m.groupValues[2]
        val hashAt = rest.indexOf('#')
        pendingHash = if (hashAt >= 0) rest.substring(hashAt + 1) else null
        if (hashAt >= 0) rest = rest.substring(0, hashAt)
        val route = name + rest
        c.navigate(route) {
            launchSingleTop = true
            if (replace) popUpTo(c.graph.id) { inclusive = true }
        }
    }

    fun reload() { _reload.value = _reload.value + 1 }

    fun isOnView(): Boolean = controller?.currentDestination?.route?.startsWith(Routes.VIEW) == true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    DisposableEffect(nav) {
        Nav.attach(nav)
        onDispose { Nav.detach(nav) }
    }
    val reload by Nav.reloadKey.collectAsState()

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(300)) }
    ) {
        // The key() re-creates the screen on reload / on every navigation, like the React <Page key=…> wrapper.
        composable(Routes.SPLASH) {
            key(reload) {
                SplashScreen(onFinished = {
                    Nav.go(if (Session.get() != null) Routes.TASK else Routes.LOGIN, replace = true)
                })
            }
        }
        composable(Routes.LOGIN) { key(reload) { LoginScreen() } }
        composable(Routes.SIGNUP) { key(reload) { SignupScreen() } }
        composable(Routes.TASK) { key(reload) { TaskScreen() } }
        composable(Routes.PROFILE) { key(reload) { PlaceholderScreen("প্রোফাইল", "ধাপ ৩-এ আসবে") } }
        composable(Routes.SUPPORT) { key(reload) { SupportScreen() } }
        composable(Routes.ADMIN) { key(reload) { AdminScreen() } }
        composable(
            Routes.VIEW_PATTERN,
            arguments = listOf(
                navArgument("book") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("page") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { key(reload) { PlaceholderScreen("পেইজ দেখুন", "ধাপ ৪-এ আসবে") } }
    }
}
