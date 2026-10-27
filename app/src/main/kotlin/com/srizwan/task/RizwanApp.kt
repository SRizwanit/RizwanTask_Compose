package com.srizwan.task

import android.app.Application
import com.srizwan.task.core.Device
import com.srizwan.task.core.Net
import com.srizwan.task.core.Prefs
import com.srizwan.task.core.ThemeStore

/** App start: SharedPreferences, saved theme, and the internet watcher (Net bar) are set up once here. */
class RizwanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Device.init(this)
        ThemeStore.load()
        Net.init(this)
    }
}
