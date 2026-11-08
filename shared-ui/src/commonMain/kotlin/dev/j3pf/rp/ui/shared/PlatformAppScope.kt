package dev.j3pf.rp.ui.shared

import kotlinx.coroutines.CoroutineScope

/**
 * Creates the platform-wired [SharedAppController] for `RemotePiApp()`.
 */
expect fun createPlatformAppController(scope: CoroutineScope): SharedAppController
