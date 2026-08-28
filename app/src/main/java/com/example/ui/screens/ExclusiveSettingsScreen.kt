package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.ui.DentalViewModel

@Composable
fun ExclusiveSettingsScreen(
    viewModel: DentalViewModel,
    isPersian: Boolean,
    onBack: () -> Unit
) {
    ClinicSettingsScreen(
        viewModel = viewModel,
        isPersian = isPersian,
        onNavigateToAccount = {},
        onBack = onBack
    )
}

