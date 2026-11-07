package dev.j3pf.rp.ui.onboarding

import androidx.compose.runtime.Composable

/** Shows a platform QR scanner and forwards each newly detected raw value. */
@Composable
expect fun QrScannerView(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
)
