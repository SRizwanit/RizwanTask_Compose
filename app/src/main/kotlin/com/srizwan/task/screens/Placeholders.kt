package com.srizwan.task.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srizwan.task.RizwanBackground
import com.srizwan.task.core.Auth
import com.srizwan.task.ui.AppCard
import com.srizwan.task.ui.HintText

/** View (and any screen not written yet) — filled in the next steps. */
@Composable
fun PlaceholderScreen(title: String, note: String, adminOnly: Boolean = false) {
    LaunchedEffect(Unit) { Auth.requireAuth(admin = adminOnly) }

    RizwanBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 440.dp)) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        HintText(note)
                    }
                }
            }
        }
    }
}
