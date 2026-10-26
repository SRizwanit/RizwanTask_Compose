package com.srizwan.task.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.srizwan.task.R

/** React `--ui: "Anek Bangla"` — the whole app UI uses it. */
val AnekBangla = FontFamily(
    Font(R.font.anekbangla, FontWeight.Normal),
    Font(R.font.anekbangla, FontWeight.Medium),
    Font(R.font.anekbanglabold, FontWeight.SemiBold),
    Font(R.font.anekbanglabold, FontWeight.Bold)
)

val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = AnekBangla),
        displayMedium = t.displayMedium.copy(fontFamily = AnekBangla),
        displaySmall = t.displaySmall.copy(fontFamily = AnekBangla),
        headlineLarge = t.headlineLarge.copy(fontFamily = AnekBangla),
        headlineMedium = t.headlineMedium.copy(fontFamily = AnekBangla),
        headlineSmall = t.headlineSmall.copy(fontFamily = AnekBangla),
        titleLarge = t.titleLarge.copy(fontFamily = AnekBangla),
        titleMedium = t.titleMedium.copy(fontFamily = AnekBangla),
        titleSmall = t.titleSmall.copy(fontFamily = AnekBangla),
        bodyLarge = t.bodyLarge.copy(fontFamily = AnekBangla),
        bodyMedium = t.bodyMedium.copy(fontFamily = AnekBangla),
        bodySmall = t.bodySmall.copy(fontFamily = AnekBangla),
        labelLarge = t.labelLarge.copy(fontFamily = AnekBangla),
        labelMedium = t.labelMedium.copy(fontFamily = AnekBangla),
        labelSmall = t.labelSmall.copy(fontFamily = AnekBangla)
    )
}
