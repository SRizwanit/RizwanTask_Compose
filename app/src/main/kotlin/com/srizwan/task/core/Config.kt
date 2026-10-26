package com.srizwan.task.core

import com.srizwan.task.BuildConfig

/**
 * React `.env` (VITE_SUPABASE_URL / VITE_SUPABASE_KEY / VITE_ADMIN_PHONE / VITE_ADMIN_WA) -> BuildConfig.
 * Values come from env vars, local.properties or gradle -P properties (see app/build.gradle.kts).
 */
object Cfg {
    val SB_URL: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val SB_KEY: String = BuildConfig.SUPABASE_KEY
    val ADMIN_PHONE: String = BuildConfig.ADMIN_PHONE
    val ADMIN_WA: String = BuildConfig.ADMIN_WA

    val configured: Boolean get() = SB_URL.isNotBlank() && SB_KEY.isNotBlank()
}
