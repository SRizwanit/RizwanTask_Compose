package com.srizwan.task

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
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
 */
class RizwanApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Device.init(this)
        ThemeStore.load()
        MotionStore.init(this)
        ViewImageDisk.init(this)
        Net.init(this)
        Push.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = CoverImages.loader(context)
}
