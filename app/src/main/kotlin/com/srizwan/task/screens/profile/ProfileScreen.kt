package com.srizwan.task.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import com.srizwan.task.LocalAppDark
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.ActivityLog
import com.srizwan.task.core.AppToast
import com.srizwan.task.core.AppUser
import com.srizwan.task.core.Auth
import com.srizwan.task.core.Fmt
import com.srizwan.task.core.Net
import com.srizwan.task.core.Phone
import com.srizwan.task.core.Sb
import com.srizwan.task.core.ThemeMode
import com.srizwan.task.core.MotionPref
import com.srizwan.task.core.MotionStore
import com.srizwan.task.core.ThemeStore
import com.srizwan.task.core.enc
import com.srizwan.task.core.jsonOf
import com.srizwan.task.core.openUrl
import com.srizwan.task.core.openWhatsApp
import com.srizwan.task.core.str
import com.srizwan.task.nav.Nav
import com.srizwan.task.screens.task.CountChip
import com.srizwan.task.screens.task.HeaderBtn
import com.srizwan.task.screens.task.HeaderTitle
import com.srizwan.task.screens.task.NoticeDialog
import com.srizwan.task.screens.task.TaskBottomNav
import com.srizwan.task.screens.task.TaskData
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.AppIcon
import com.srizwan.task.ui.AppLogo
import com.srizwan.task.ui.BlockButton
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.OutlineButton
import com.srizwan.task.ui.PalettePicker
import com.srizwan.task.ui.SegChoice
import com.srizwan.task.ui.ThemeToggleButton
import com.srizwan.task.ui.errColor
import com.srizwan.task.ui.goldColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** React Profile.jsx `PM`. */
val PM_LABEL = mapOf("bkash" to "বিকাশ", "nagad" to "নগদ")

/**
 * React `Profile` (Profile.jsx), built in pieces (3-চ … 3-ছ):
 *   header.top        -> [ProfileHeader]: logo, "প্রোফাইল", "N টি সম্পন্ন" chip, theme toggle, admin / logout (admin only)   3-চ-১
 *   section#pProf     -> [ProfileCard]:  avatar, name, role badge, completed-task stat, mobile / payment number / join date,
 *                        theme switch, logout, guide, ticket and WhatsApp buttons                                          3-চ-১
 *                        income stat `incCard` (মোট পয়েন্টস + উত্তোলনযোগ্য / রিভিউ বাকি, from `TaskData.loadMoney` / `sum`)     3-ছ-২
 *                        per-book progress `profProg` -> [ProfileProgress] / [ProgLine] (ProfileProgress.kt, `loadCounts`)   3-ছ-৩
 *                        "নোটিশ ও ঘোষণা" button (red unseen badge) -> `onNoticesBtn` -> [NoticeDialog] (Task page's), "seen" memory in
 *                        [TaskData] (`readSeen/writeSeen`, key `kp_notice_seen_<id>`)                                      3-ছ-১
 *   dialog#dlg        -> [PaymentEditDialog] (ProfileDialogs.kt) + `openEdit` / `saveEdit`: change my payment method / number   3-চ-২
 *   nav.bottom        -> [TaskBottomNav] with no tab lit (task / payment / rank open the Task page on that tab)
 * Not converted on purpose: the PWA "হোম স্ক্রিনে ইনস্টল করুন" button (`onInstall`) and the nav re-pinning effect (a normal
 * layout item cannot leave the screen).
 * The data comes from the same [TaskData] as the Task page (the React Profile had its own copy of the loaders).
 */
@Composable
fun ProfileScreen() {
    val data = remember { TaskData() }
    val me = data.me
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    val isAdmin = me?.let { Phone.isAdminPhone(it.phone) } == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    /** React `logout`: log the action, then sign out after a short delay. */
    fun logout() {
        scope.launch { ActivityLog.log("logout", ""); delay(150); Auth.logout() }
    }

    /** React `whatsapp`: a ready help message to the admin. */
    fun whatsapp() {
        val m = "আসসালামু আলাইকুম, আমি কিতাব প্রুফিং অ্যাপের ইউজার। নাম: " + (me?.fullName?.takeIf { it.isNotEmpty() } ?: "-") +
            "। ফোন নাম্বার: " + (me?.phone?.takeIf { it.isNotEmpty() } ?: "-") + "। আমার একটি সাহায্য প্রয়োজন।"
        openWhatsApp(ctx, m)
    }

    // ---- payment number edit (React `edit` state, `dlg`, `openEdit`, `saveEdit`) ----
    var editOpen by remember { mutableStateOf(false) }
    var editPm by remember { mutableStateOf("bkash") }
    var editNum by remember { mutableStateOf("") }
    var editErr by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }

    /** React `openEdit`: fill the form from my row (method defaults to বিকাশ), clear the error, open the dialog. */
    fun openEdit() {
        val u = data.me ?: return
        editPm = u.paymentMethod?.takeIf { it.isNotEmpty() } ?: "bkash"
        editNum = u.paymentNumber ?: ""
        editErr = ""
        editOpen = true
    }

    /**
     * React `saveEdit`: validate the number (`Phone.validate`), PATCH `app_users?id=eq.<me>` with `payment_method` +
     * `payment_number` (the normalised number), keep the returned row as the new `me`, close, toast "সংরক্ষিত হয়েছে"
     * and log `payment_number_changed` ("<বিকাশ|নগদ> <number>"). A failure shows its message in the dialog.
     */
    fun saveEdit() {
        val u = data.me ?: return
        if (editBusy) return
        val v = Phone.validate(editNum)
        if (!v.ok) { editErr = v.msg; return }
        val m = editPm
        editBusy = true
        scope.launch {
            try {
                val r = Sb.patch("app_users?id=eq.${enc(u.id)}", jsonOf("payment_method" to m, "payment_number" to v.phone))
                // React: `r[0] || { ...me, payment_method, payment_number }`
                val nu = r.optJSONObject(0)?.let { AppUser(it) } ?: AppUser(org.json.JSONObject(u.raw.toString()).apply {
                    put("payment_method", m); put("payment_number", v.phone)
                })
                data.me = nu
                editOpen = false
                AppToast.show("সংরক্ষিত হয়েছে")
                ActivityLog.log("payment_number_changed", (PM_LABEL[m] ?: m) + " " + v.phone)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                editErr = e.message ?: ""
            } finally { editBusy = false }
        }
    }

    /**
     * React `onNoticesBtn`: load the notices; none -> toast "এখনো কোনো নোটিশ নেই"; otherwise open the dialog with all of them
     * (newest first), unless it is already open. Closing marks every shown notice as seen and recounts the badge
     * (React `onNoticeClose`, here [TaskData.closeNotices]).
     */
    fun onNoticesBtn() {
        scope.launch {
            val list = data.loadNotices(autoOpen = false)
            if (list.isEmpty()) { AppToast.show("এখনো কোনো নোটিশ নেই"); return@launch }
            if (!data.nOpen) data.showNotices(list)
        }
    }

    // React init: login check, then the first load (count, money, per-book counts, notices).
    LaunchedEffect(Unit) {
        val u = Auth.requireAuth() ?: return@LaunchedEffect
        data.me = u
        scope.launch { data.loadCount() }
        scope.launch { data.loadMoney() }
        scope.launch { data.loadCounts() }
        scope.launch { data.loadNotices(autoOpen = false) }
    }

    // React setInterval(loadNotices, 180000): every 3 minutes while the app is on screen.
    LaunchedEffect(me != null) {
        if (me == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { delay(180000); data.loadNotices(autoOpen = false) } }
    }

    // React visibilitychange / pageshow: coming back to the app refreshes the numbers (the replayed first ON_START is skipped).
    DisposableEffect(lifecycle, me) {
        var ready = false
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && ready && data.me != null) {
                scope.launch { data.loadCount() }
                scope.launch { data.loadMoney() }
                scope.launch { data.loadCounts() }
                scope.launch { data.loadNotices(autoOpen = false) }
            }
        }
        lifecycle.addObserver(obs); ready = true
        onDispose { lifecycle.removeObserver(obs) }
    }
    // React kp-retry: the net bar's retry button reloads the data.
    DisposableEffect(me) {
        val h: () -> Boolean = {
            if (data.me == null) false else {
                scope.launch { data.loadCount() }
                scope.launch { data.loadMoney() }
                scope.launch { data.loadCounts() }
                scope.launch { data.loadNotices(autoOpen = false) }
                true
            }
        }
        Net.addRetryHandler(h)
        onDispose { Net.removeRetryHandler(h) }
    }

    // Back from the Profile always goes to the Task page (an open dialog is closed by its own handler first).
    BackHandler { Nav.go("task") }

    RizwanBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ProfileHeader(data.myCount, isAdmin, onAdmin = { Nav.go("admin") }, onLogout = { logout() })
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 980.dp).fillMaxSize().verticalScroll(scroll)
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.widthIn(max = 440.dp)) {
                        ProfileCard(
                            data, isAdmin,
                            onEdit = { openEdit() },
                            onNotices = { onNoticesBtn() },
                            onLogout = { logout() },
                            onGuide = { Nav.go("task#guide") },
                            onTicket = { Nav.go("support") },
                            onWhatsApp = { whatsapp() },
                            onPrivacy = { openUrl(ctx, PRIVACY_POLICY_URL) },
                            onDeleteAccount = { Nav.go("deleteaccount") }
                        )
                    }
                }
            }
            if (me != null) TaskBottomNav("", onPick = { Nav.go("task#$it") })
        }
    }

    // React `#nDlg`: the notices one by one; any way of closing marks them as seen
    if (data.nOpen) NoticeDialog(data.nList, data.nIdx, onIdx = { data.nIdx = it }, onClose = { data.closeNotices() })

    if (editOpen) {
        PaymentEditDialog(
            pm = editPm, onPm = { editPm = it },
            num = editNum, onNum = { editNum = it },
            err = editErr, busy = editBusy,
            onCancel = { editOpen = false },
            onSave = { saveEdit() }
        )
    }
}

/** React `header.top` of the profile page. */
@Composable
private fun ProfileHeader(myCount: Int?, isAdmin: Boolean, onAdmin: () -> Unit, onLogout: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 430.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLogo(size = 30.dp)
            if (!isAdmin || wide) HeaderTitle("প্রোফাইল", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (myCount != null) CountChip(myCount)
            ThemeToggleButton()
            if (isAdmin) {
                HeaderBtn(if (wide) "অ্যাডমিন" else "", "shield", onClick = onAdmin)
                HeaderBtn(if (wide) "লগআউট" else "", "logout", onClick = onLogout)
            }
        }
    }
}

/** React `section#pProf > .card`. */
@Composable
private fun ProfileCard(
    data: TaskData, isAdmin: Boolean,
    onEdit: () -> Unit, onNotices: () -> Unit, onLogout: () -> Unit, onGuide: () -> Unit, onTicket: () -> Unit, onWhatsApp: () -> Unit,
    onPrivacy: () -> Unit, onDeleteAccount: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val me = data.me
    val myCount = data.myCount
    AppCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // avatar: first letter of the name on the brand gradient (React `.av`)
            val initial = me?.fullName?.trim()?.take(1)?.uppercase() ?: ""
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
                contentAlignment = Alignment.Center
            ) { Text(initial, color = cs.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            Text(
                me?.fullName ?: "", color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            RoleBadge(if (isAdmin) "অ্যাডমিন" else "ইউজার")

            // completed-task stat (React `.stat`; `#stN` shows ০ until the user is known, then the count or –)
            Spacer(Modifier.height(8.dp))
            StatBox("check", if (myCount == null && me == null) "০" else if (myCount == null) "–" else Fmt.bn(myCount), " টি টাস্ক সম্পন্ন")
            // React `#incCard`: total earned + the small "withdrawable / pending review" line (before the first load: ০.০০ পয়েন্টস, no line)
            val sum = data.sum
            Spacer(Modifier.height(10.dp))
            StatBox(
                "points", if (sum != null) Fmt.money(sum.earned) else "০.০০ পয়েন্টস", " মোট পয়েন্টস",
                sub = if (sum != null) "উত্তোলনযোগ্য " + Fmt.money(sum.available) +
                    (if (sum.pendingAmt > 0) " • রিভিউ বাকি " + Fmt.money(sum.pendingAmt) else "") else null
            )

            // key / value rows
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                KvRow("মোবাইল নাম্বার", me?.phone ?: "")
                KvRow(
                    if (me != null) (PM_LABEL[me.paymentMethod] ?: "পেমেন্ট") + " নাম্বার" else "বিকাশ নাম্বার",
                    if (me != null) me.paymentNumber?.takeIf { it.isNotEmpty() } ?: "-" else ""
                )
                KvRow("যোগ দিয়েছেন", if (me != null) me.raw.str("created_at")?.let { Fmt.fmtD(it) } ?: "-" else "")
            }
            // React `#profProg`: "আমার কাজ — কিতাব অনুযায়ী" (only when I have proofed pages)
            ProfileProgress(data, Modifier.padding(top = 16.dp))

            // theme (React `#themeSeg`)
            FieldLabel("থিম (দিন/রাত)", Modifier.padding(top = 2.dp))
            SegChoice(
                listOf("auto" to "সিস্টেম", "light" to "লাইট", "dark" to "ডার্ক"),
                selected = when (ThemeStore.mode) { ThemeMode.AUTO -> "auto"; ThemeMode.LIGHT -> "light"; ThemeMode.DARK -> "dark" },
                onSelect = { v ->
                    ThemeStore.set(when (v) { "light" -> ThemeMode.LIGHT; "dark" -> ThemeMode.DARK; else -> ThemeMode.AUTO })
                    AppToast.show("থিম: " + ThemeStore.label())
                }
            )

            // design theme: garden / ocean / twilight (colours + animated background); day / night stays above
            FieldLabel("ডিজাইন থিম", Modifier.padding(top = 2.dp))
            PalettePicker(
                selected = ThemeStore.palette,
                onSelect = { p ->
                    ThemeStore.setPalette(p)
                    AppToast.show("ডিজাইন থিম: " + p.label)
                }
            )

            // background animation: auto (battery saver / phone animations off) / full / slow / still
            FieldLabel("অ্যানিমেশন কমান", Modifier.padding(top = 2.dp))
            SegChoice(
                MotionPref.entries.map { it.key to it.label },
                selected = MotionStore.pref.key,
                onSelect = { v ->
                    val m = MotionPref.of(v)
                    MotionStore.set(m)
                    AppToast.show("ব্যাকগ্রাউন্ড অ্যানিমেশন: " + m.label)
                }
            )
            Text(
                MotionStore.hint(), color = cs.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )

            // React `#bEdit`: opens the payment number dialog
            BlockButton("পেমেন্ট নাম্বার পরিবর্তন", onClick = onEdit, modifier = Modifier.padding(top = 16.dp), icon = "edit")
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("লগআউট", onClick = onLogout, color = errColor(), icon = "logout")
                // React `#bNotices`: bell icon + the unseen count (`.tbadge`)
                OutlineButton("নোটিশ ও ঘোষণা", onClick = onNotices, color = cs.primary, icon = "bell", badge = data.badge)
                OutlineButton("ব্যবহারের গাইড", onClick = onGuide, color = cs.primary, icon = "info")
                // (the PWA install button is not needed in a native app)
                // "টিকেট খুলুন" button hidden (onTicket kept so it can be restored)
                OutlineButton("হোয়াটসঅ্যাপে যোগাযোগ করুন", onClick = onWhatsApp, color = waColor(), icon = "whatsapp")
                // privacy policy (opens in the browser) + the "অ্যাকাউন্ট মুছুন" screen
                OutlineButton("প্রাইভেসি পলিসি", onClick = onPrivacy, color = cs.primary, icon = "shield")
                OutlineButton("অ্যাকাউন্ট মুছুন", onClick = onDeleteAccount, color = errColor(), icon = "trash")
            }
        }
    }
}

/** React `button.help.wa`: WhatsApp green. */
@Composable
private fun waColor(): Color = if (LocalAppDark.current) Color(0xFF25D366) else Color(0xFF128C7E)

/** React `.badge`: small gold-tinted pill. */
@Composable
private fun RoleBadge(text: String) {
    val gold = goldColor()
    Box(Modifier.clip(CircleShape).background(gold.copy(alpha = 0.16f)).border(1.dp, gold.copy(alpha = 0.55f), CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** React `.stat`: round icon + big number + label. [sub] = the small line under it (used by the income stat in 3-ছ-২). */
@Composable
internal fun StatBox(icon: String, big: String, label: String, sub: String? = null) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(cs.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(goldColor().copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            AppIcon(icon, size = 18.dp, tint = goldColor())
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(big, color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            if (!sub.isNullOrEmpty()) Text(sub, color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** React `.kv`: label on the left, value on the right, a thin line under each row. */
@Composable
internal fun KvRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = cs.onSurfaceVariant, fontSize = 13.5.sp)
            Text(value, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline.copy(alpha = 0.35f)))
    }
}
