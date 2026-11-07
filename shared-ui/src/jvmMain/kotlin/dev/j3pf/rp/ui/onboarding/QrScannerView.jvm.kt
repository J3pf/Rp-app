package dev.j3pf.rp.ui.onboarding

import androidx.compose.runtime.Composable

@Suppress("UNUSED_PARAMETER")
@Composable
actual fun QrScannerView(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) = Unit
