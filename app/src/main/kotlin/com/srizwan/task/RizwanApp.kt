package com.srizwan.task

import android.app.Application
import android.content.Context
import android.os.Process
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.srizwan.task.core.CrashHandler
import com.srizwan.task.core.Crumb
import com.srizwan.task.core.Device
import com.srizwan.task.core.MotionStore
import com.srizwan.task.core.Net
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.ThemeStore
import com.srizwan.task.core.ViewImageDisk
import com.srizwan.task.push.Push
import com.srizwan.task.ui.CoverImages

/**
 * App start: SharedPreferences, saved theme, the animation setting (battery saver watcher), the View page image disk cache and
 * the internet watcher (Net bar) are set up once here. Also the app's one Coil image loader (book covers: memory + disk cache).
 *
 * Crash catching starts in attachBaseContext (the earliest app code, before the content providers) and every start step
 * is written to a small file ([Crumb]), so the "RizwanTask Log" screen can show where a silent close happened.
 * The ":crash" / ":log" processes (debug screens) skip all of it.
 */
class RizwanApp : Application(), SingletonImageLoader.Factory {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (CrashHandler.isAuxProcess()) return
        Crumb.start(this)
        Crumb.mark(this, "Application.attachBaseContext (pid " + Process.myPid() + ")")
        CrashHandler.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (CrashHandler.isAuxProcess()) return
        Crumb.mark(this, "Application.onCreate start")
        Prefs.init(this); Crumb.mark(this, "Prefs ok")
        Device.init(this); Crumb.mark(this, "Device ok")
        ThemeStore.load(); Crumb.mark(this, "ThemeStore ok")
        MotionStore.init(this); Crumb.mark(this, "MotionStore ok")
        ViewImageDisk.init(this); Crumb.mark(this, "ViewImageDisk ok")
        Net.init(this); Crumb.mark(this, "Net ok")
        Push.init(this); Crumb.mark(this, "Push ok")
        Crumb.mark(this, "Application.onCreate done")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = CoverImages.loader(context)
}
