package com.srizwan.task.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.core.Fmt
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.AppLogo
import com.srizwan.task.ui.ThemeToggleButton
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.goldColor

/** React Task.jsx `TITLES`. */
val TASK_TITLES = mapOf("task" to "টাস্কের তালিকা", "pay" to "পেমেন্ট", "rank" to "লিডারবোর্ড")

/** React Task.jsx `NAV`: (tab, icon, label). */
private val NAV = listOf(
    Triple("task", "tasks", "টাস্ক"),
    Triple("pay", "wallet", "পেমেন্ট"),
    Triple("rank", "trophy", "র‍্যাংক")
)

/**
 * React `header.top`: small logo, page title, "N টি সম্পন্ন" chip, theme toggle, profile button (red badge for new
 * notices), and for the admin the "অ্যাডমিন" and "লগআউট" buttons.
 * [myCount] null = chip hidden (filled by the loader in 3-ক-২); [badge] = unseen notice count (3-খ-৩ / 3-ছ-১).
 */
@Composable
fun TaskHeader(
    tab: String,
    myCount: Int?,
    badge: Int,
    isAdmin: Boolean,
    onProfile: () -> Unit,
    onAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppLogo(size = 30.dp)
        Text(
            TASK_TITLES[tab].orEmpty(), color = cs.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (myCount != null) CountChip(myCount)
        ThemeToggleButton()
        // profile button + red badge
        Box {
            HeaderBtn("", "user", onClick = onProfile)
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).clip(CircleShape).background(errColor())
                        .padding(horizontal = 5.dp)
                ) { Text(if (badge > 99) "৯৯+" else Fmt.bn(badge), color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (isAdmin) {
            HeaderBtn("অ্যাডমিন", "shield", onClick = onAdmin)
            HeaderBtn("লগআউট", "logout", onClick = onLogout)
        }
    }
}

/** React `.cnt`: gold pill "✓ N টি সম্পন্ন" (the tick is a vector icon). */
@Composable
internal fun CountChip(n: Int) {
    val cs = MaterialTheme.colorScheme
    val gold = goldColor()
    Row(
        Modifier.clip(CircleShape).background(gold.copy(alpha = 0.16f)).border(1.dp, gold.copy(alpha = 0.55f), CircleShape)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AppIcon("check", size = 14.dp, tint = gold)
        Text(Fmt.bn(n), color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("টি সম্পন্ন", color = cs.onSurfaceVariant, fontSize = 11.5.sp)
    }
}

@Composable
internal fun HeaderBtn(text: String, icon: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AppIcon(icon, size = 16.dp, tint = cs.onSurface)
        if (text.isNotEmpty()) Text(text, color = cs.onSurface, fontSize = 13.sp)
    }
}

/**
 * React `nav.bottom`: task / payment / rank buttons; the open one is highlighted.
 * (React `usePinNav` / `repin` keeps the bar inside the mobile-browser viewport — not needed here: the bar is a normal
 * layout item below the content, so it can never leave the screen.)
 */
@Composable
fun TaskBottomNav(tab: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().background(cs.surface.copy(alpha = 0.97f))
            .border(1.dp, cs.outline.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.widthIn(max = 980.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            NAV.forEach { (k, icon, label) ->
                val on = tab == k
                val tint = if (on) cs.primary else cs.onSurfaceVariant
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onPick(k) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppIcon(icon, size = 22.dp, tint = tint)
                    Text(label, color = tint, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
