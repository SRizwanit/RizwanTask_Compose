package com.srizwan.task.screens.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.srizwan.task.ui.viewColors
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * React `window.confirm(msg)` of `View.jsx` as a dialog: [ask] shows [msg] and waits for the answer (yes = true, no / outside tap / back = false).
 * It is the page's `st.confirm` (save and next on an empty page, discard, API replacing text ...). While it is open [open] is true, so
 * auto OCR does not start (React `dialog[open]`). A second question while one is open answers the first with no.
 */
class ViewConfirmState {
    var msg: String? by mutableStateOf(null); private set
    val open: Boolean get() = msg != null
    private var cont: CancellableContinuation<Boolean>? = null

    suspend fun ask(message: String): Boolean = suspendCancellableCoroutine { c ->
        cont?.let { if (it.isActive) it.resume(false) }
        cont = c; msg = message
        c.invokeOnCancellation { if (cont === c) { cont = null; msg = null } }
    }

    fun answer(yes: Boolean) {
        val c = cont; cont = null; msg = null
        if (c != null && c.isActive) c.resume(yes)
    }
}

/** The dialog of [ViewConfirmState]: same card as the other View dialogs (16 dp corners, 18 dp padding), "বাতিল" and "ঠিক আছে". */
@Composable
fun ViewConfirmDialog(state: ViewConfirmState) {
    val m = state.msg ?: return
    val c = viewColors()
    Dialog(onDismissRequest = { state.answer(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(m, color = c.fg, fontSize = 15.sp, lineHeight = 23.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = { state.answer(false) }, border = BorderStroke(1.dp, c.line)) { Text("বাতিল", color = c.fg) }
                Button(onClick = { state.answer(true) }, colors = ButtonDefaults.buttonColors(containerColor = c.pri, contentColor = c.priFg)) {
                    Text("ঠিক আছে", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
