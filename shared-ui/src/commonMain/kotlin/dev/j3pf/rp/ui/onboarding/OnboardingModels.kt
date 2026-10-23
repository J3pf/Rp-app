package dev.j3pf.rp.ui.onboarding

data class PairUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)
