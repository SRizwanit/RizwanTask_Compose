package com.srizwan.task.screens.task

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * React task/guideScenes.js — the animation engine (3-ঙ-২): `FPS`, `clamp`, `E`, `interpolate`, `spring`, `bd`, `fadeIO`
 * and the player (`playScene` / `stopAnim`).
 *
 * React draws every scene as an SVG whose elements are changed by `mount(q)` on each frame. Compose has no DOM, so a scene
 * is a [GuideScene]: a size in the same "viewBox" units as React (w x h), a length in frames ([dur]) and a pure
 * `draw(f)` that paints frame f on a Canvas in those units (the player scales the units to the screen width), plus
 * `phaseAt(f)` = what React's `upd(f)` returned (the active sub-step number, or null). The scenes themselves
 * (shotScene + sceneWelcome in 3-ঙ-৩ — GuideShot.kt / SceneWelcome.kt; sceneBook … sceneCommunity in 3-ঙ-৪ … ৫).
 */

const val FPS = 30

/** "all sub-steps lit" — React `onPhase('all')`, used when the system animations are off (reduced motion). */
const val PHASE_ALL = -2

/** React `clamp`. */
fun clamp(v: Float, a: Float, b: Float): Float = Math.min(b, Math.max(a, v))

/** React `E`: easing functions (t in 0..1). */
object E {
    val lin: (Float) -> Float = { t -> t }
    val out: (Float) -> Float = { t -> 1f - Math.pow((1f - t).toDouble(), 3.0).toFloat() }
    val inOut: (Float) -> Float = { t ->
        if (t < .5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f
    }
}

/**
 * React `interpolate(f, inR, outR, ease)`: maps f over the input points to the output points (clamped at both ends,
 * piece by piece linear; [ease] is applied to each piece).
 */
fun interpolate(f: Number, inR: List<Number>, outR: List<Number>, ease: ((Float) -> Float)? = null): Float {
    val x = f.toFloat()
    val n = inR.size
    if (x <= inR[0].toFloat()) return outR[0].toFloat()
    if (x >= inR[n - 1].toFloat()) return outR[n - 1].toFloat()
    var i = 0
    while (i < n - 2 && x > inR[i + 1].toFloat()) i++
    val span = inR[i + 1].toFloat() - inR[i].toFloat()
    var t = (x - inR[i].toFloat()) / (if (span == 0f) 1f else span)
    if (ease != null) t = ease(t)
    val a = outR[i].toFloat()
    return a + (outR[i + 1].toFloat() - a) * t
}

/** React `spring(f, {stiffness, damping, mass})`: a damped spring 0 -> 1 (frames at [FPS]). */
fun spring(f: Number, stiffness: Number = 110, damping: Number = 11, mass: Number = 1): Float {
    val k = stiffness.toDouble(); val c = damping.toDouble(); val m = mass.toDouble()
    val t = Math.max(0.0, f.toDouble()) / FPS
    val w0 = Math.sqrt(k / m)
    val z = c / (2 * Math.sqrt(k * m))
    if (z < 1) {
        val wd = w0 * Math.sqrt(1 - z * z)
        return (1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + (z * w0 / wd) * Math.sin(wd * t))).toFloat()
    }
    return (1 - Math.exp(-w0 * t) * (1 + w0 * t)).toFloat()
}

/** React `bd`: Bangla digits. */
fun bd(s: Any): String = s.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

/** React `fadeIO`: fade in over the first 8 frames, out over the last 10. */
fun fadeIO(f: Number, dur: Number): Float =
    interpolate(f, listOf(0, 8), listOf(0, 1)) * interpolate(f, listOf(dur.toFloat() - 10f, dur), listOf(1, 0))

/** What a scene needs while drawing: text measuring and the guide screenshots ([GuideImages] keys, decoded once). */
class SceneEnv(val text: TextMeasurer, private val loader: (String) -> ImageBitmap?) {
    fun image(key: String): ImageBitmap? = loader(key)
}

/**
 * One animated scene (React `{ svg, dur, mount }`). [w] x [h] = the viewBox; [dur] = frames; [phaseAt] = React `upd(f)`'s
 * return value (active sub-step number, null = unchanged); [draw] paints frame `f` in viewBox units (0,0 = top-left).
 */
class GuideScene(
    val w: Float,
    val h: Float,
    val dur: Int,
    val phaseAt: (Int) -> Int? = { null },
    val draw: DrawScope.(f: Int, env: SceneEnv) -> Unit
)

/** React `matchMedia('(prefers-reduced-motion: reduce)')` -> Android: the system "remove animations" setting. */
private fun reducedMotion(ctx: Context): Boolean = try {
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) { false }

/**
 * React `playScene(sc, stage, onPhase)` + `stopAnim`: plays [scene] in a loop at [FPS] and reports the active sub-step
 * through [onPhase] (only when it changes; [PHASE_ALL] with reduced motion, which shows one still frame at 72%).
 * The loop lives in a LaunchedEffect, so changing the scene or leaving the screen stops it (React `stopAnim`).
 * [scene] = null draws nothing (an empty stage).
 */
@Composable
fun GuideStage(scene: GuideScene?, onPhase: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tm = rememberTextMeasurer()
    val images = remember { HashMap<String, ImageBitmap>() }
    val env = remember(tm) {
        SceneEnv(tm) { key ->
            images[key] ?: GuideImages[key]?.let { g -> ImageBitmap.imageResource(ctx.resources, g.res).also { images[key] = it } }
        }
    }
    var frame by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        val sc = scene ?: return@LaunchedEffect
        if (reducedMotion(ctx)) {
            frame = Math.floor(sc.dur * .72).toInt()
            onPhase(PHASE_ALL)
            return@LaunchedEffect
        }
        var t0 = -1L
        var last = -1
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (t0 < 0) t0 = now
                val f = Math.floor((now - t0) / 1e9 * FPS).toInt() % sc.dur
                frame = f
                val ph = sc.phaseAt(f)
                if (ph != null && ph != last) { last = ph; onPhase(ph) }
            }
        }
    }
    val w = scene?.w ?: 400f
    val h = scene?.h ?: 200f
    Canvas(modifier.aspectRatio(w / h)) {
        if (scene != null) scale(size.width / w, pivot = Offset.Zero) { scene.draw(this, frame, env) }
    }
}
