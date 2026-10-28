package com.srizwan.task.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srizwan.task.ui.AppCard

/**
 * Loading skeletons shared by all three tabs (task / payment / rank), so a first load looks the same everywhere:
 * the same shimmer ([shimmer], TaskBooks.kt), the same card edge, the same rounded blocks.
 * The book grid keeps its own card-shaped skeleton (TaskBooks.kt `SkeletonCard`); the pay tab's tiles skeleton is
 * `PayTilesSkeleton` (TaskPay.kt).
 */

/** One shimmering line, [widthFraction] of the width. */
@Composable
internal fun SkeletonLine(widthFraction: Float, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Box(modifier.fillMaxWidth(widthFraction).height(height).clip(RoundedCornerShape(if (height > 10.dp) 6.dp else 99.dp)).shimmer())
}

/** Several [SkeletonLine]s one under the other (content of a card that is still loading). */
@Composable
internal fun SkeletonStack(lines: List<Float>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { SkeletonLine(it) }
    }
}

/** A whole card with shimmering lines (the pay tab's forecast while the money data loads). */
@Composable
internal fun SkeletonPanel(lines: List<Float>) {
    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) { SkeletonStack(lines) }
}

/** Rank tab while the first load runs: a podium of three and a few list rows. */
@Composable
internal fun RankSkeleton() {
    AppCard(modifier = Modifier.padding(bottom = 14.dp), padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { i ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (i == 0) 56.dp else 46.dp).clip(CircleShape).shimmer())
                    SkeletonLine(0.7f, Modifier.padding(top = 8.dp))
                    SkeletonLine(0.5f, Modifier.padding(top = 6.dp), height = 10.dp)
                    Box(
                        Modifier.padding(top = 8.dp).fillMaxWidth().height(PODIUM_H[i])
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).shimmer()
                    )
                }
            }
        }
    }
    repeat(4) { RankRowSkeleton() }
}

/** `.lbr` placeholder: rank circle, name, page count. */
@Composable
private fun RankRowSkeleton() {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape)
            .background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outline.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).shimmer())
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Box(Modifier.width(56.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
    }
}
