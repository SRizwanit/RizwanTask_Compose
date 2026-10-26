package com.srizwan.task.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.ui.AppField
import com.srizwan.task.ui.FieldLabel
import com.srizwan.task.ui.SegChoice
import com.srizwan.task.ui.errColor

/**
 * React Profile.jsx `<dialog id="dlg">` — "পেমেন্ট নাম্বার": pick the method (বিকাশ / নগদ) and type the number.
 * Opening / closing is done by the parent (a `show` flag in ProfileScreen instead of `dlg.showModal()/close()`);
 * the checks and the save are in ProfileScreen `saveEdit`. [err] shows under the field (red); [busy] disables the
 * save button while the request is running (React had no such guard — it only avoids a double submit).
 */
@Composable
fun PaymentEditDialog(
    pm: String,
    onPm: (String) -> Unit,
    num: String,
    onNum: (String) -> Unit,
    err: String,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("পেমেন্ট নাম্বার", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // label.f + .seg radios
                FieldLabel("মাধ্যম", first = true)
                SegChoice(listOf("bkash" to "বিকাশ", "nagad" to "নগদ"), selected = pm, onSelect = onPm)
                // label.f + input#pn [type=tel, maxLength=18]
                FieldLabel("নাম্বার", Modifier.padding(top = 4.dp))
                AppField(
                    value = num,
                    onValueChange = onNum,
                    keyboardType = KeyboardType.Phone,
                    maxLength = 18,
                    imeAction = ImeAction.Done,
                    onAction = { if (!busy) onSave() },
                    enabled = !busy
                )
                // .err#dErr
                if (err.isNotEmpty()) Text(err, color = errColor(), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !busy) { Text("সংরক্ষণ", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("বন্ধ") } }
    )
}
