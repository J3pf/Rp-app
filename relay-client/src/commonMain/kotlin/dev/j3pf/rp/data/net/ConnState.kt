package dev.j3pf.rp.data.net

sealed interface ConnState {
    data object Disconnected : ConnState

    data object Connecting : ConnState

    data object Connected : ConnState

    /** Cierre fatal (token revocado / versión incompatible): no se reintenta. */
    data class Fatal(
        val code: Int,
        val reason: String,
    ) : ConnState
}
