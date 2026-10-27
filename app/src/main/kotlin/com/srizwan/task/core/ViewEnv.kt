package com.srizwan.task.core

import android.content.Context

/**
 * Everything `ViewPage` creates once per opened book (React hooks/refs of the page): context, config, toaster, API flag, GitHub, image cache.
 * Later steps add their own pieces here or next to it; screens take one `ViewEnv` instead of a long parameter list.
 */
class ViewEnv(val ctx: ViewCtx, val toaster: ViewToaster, val apiFlag: ViewApiFlag) {
    val cfg = ViewCfgStore(ctx)

    /** React `gh = createGithub(() => cfgRef.current)` */
    val gh = ViewGithub { cfg.cfg }

    /** Set by the image area (step 4-ga-2: the percentage shown while the current image loads); React `imgProg`. */
    var onImgProgress: (name: String, pct: Float?) -> Unit = { _, _ -> }

    /** React `imgCache = gh.createImageCache((n, p) => imgProg(n, p))` */
    val imgCache = gh.createImageCache { n, p -> onImgProgress(n, p) }

    /** React `downloadCurrent` — [name] = file name of the open image (null = none open). Saves the original to Downloads. */
    suspend fun downloadCurrent(context: Context, name: String?) = ViewDownload.downloadCurrent(context, gh, toaster, name)

    /** React `imageB64(im)` (step 4-chha-1) — page image [name] as base64 for Gemini; the API steps (4-chha-2/3) build the `parts` from it. */
    suspend fun imageB64(name: String): ViewImgB64 = ViewGemini.imageB64(gh, imgCache, name)
}
