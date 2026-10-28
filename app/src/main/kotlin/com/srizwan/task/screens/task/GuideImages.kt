package com.srizwan.task.screens.task

import androidx.annotation.DrawableRes
import com.srizwan.task.R

/**
 * React task/guideImages.js (3-ঙ-১): the guide's screenshots. In React they are base64 WebP strings (`IMG[key]`);
 * here each one is a file in `res/drawable` (`guide_<key>.webp`, bytes identical to the decoded base64).
 * Used by the guide scenes (3-ঙ-৩ … ৫) through [GuideImages.get]; [w] x [h] = the pixel size of the image
 * (the scenes draw it stretched to their own width / height, `preserveAspectRatio="none"`).
 */
class GuideImage(@param:DrawableRes val res: Int, val w: Int, val h: Int)

object GuideImages {
    private val all: Map<String, GuideImage> = mapOf(
        "a" to GuideImage(R.drawable.guide_a, 400, 124),
        "b" to GuideImage(R.drawable.guide_b, 680, 245),
        "c" to GuideImage(R.drawable.guide_c, 680, 170),
        "d0" to GuideImage(R.drawable.guide_d0, 380, 175),
        "d" to GuideImage(R.drawable.guide_d, 380, 175),
        "e" to GuideImage(R.drawable.guide_e, 720, 64)
    )

    /** React `IMG[key]`; null for an unknown key. */
    operator fun get(key: String): GuideImage? = all[key]
}
